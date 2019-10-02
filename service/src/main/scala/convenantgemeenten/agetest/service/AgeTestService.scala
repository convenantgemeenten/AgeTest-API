package convenantgemeenten.agetest.service

import java.time.Instant
import java.time.format.DateTimeFormatter

import cats.effect.IO
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Http, Service}
import com.twitter.io.Buf
import com.twitter.server.TwitterServer
import convenantgemeenten.agetest.endpoint._
import convenantgemeenten.agetest.ns.AgeTest
import io.finch.{
  Application,
  Bootstrap,
  Endpoint,
  InternalServerError,
  NotAcceptable,
  Ok,
  Text
}
import lspace._
import lspace.codec.json.jsonld.JsonLDEncoder
import lspace.codec.{ActiveContext, json}
import lspace.decode.{DecodeJson, DecodeJsonLD}
import lspace.encode.{EncodeJson, EncodeJsonLD}
import lspace.ns.vocab.schema
import lspace.provider.detached.DetachedGraph
import lspace.services.LService
import lspace.services.codecs.{Application => LApplication}
import monix.eval.Task
import monix.execution.Scheduler
import monix.reactive.Observable
import shapeless.{:+:, CNil}

import scala.concurrent.Await
import scala.util.Try

object AgeTestService extends LService with TwitterServer {
  lazy val ageGraph: Graph = Graph("ageGraph")
  lazy val ageTestGraph: Graph = Graph("ageTestGraph")

  import lspace.codec.argonaut._
  implicit val ec: Scheduler = lspace.Implicits.Scheduler.global

  implicit val encoderJsonLD = JsonLDEncoder.apply(nativeEncoder)
  implicit val decoderJsonLD =
    lspace.codec.json.jsonld.JsonLDDecoder.apply(DetachedGraph)(nativeDecoder)
  implicit val decoderGraphQL = codec.graphql.Decoder
  import lspace.Implicits.AsyncGuide.guide
  implicit lazy val activeContext = AgeTestEndpoint.activeContext

  lazy val agetestEndpoint =
    AgeTestEndpoint(
      ageGraph,
      ageTestGraph,
      "http://demo.convenantgemeenten.nl/agetest/") //TODO: get from config

  object UtilsApi extends Endpoint.Module[IO] {
    import io.finch._

    def reset(): Task[Unit] =
      for {
        //      SampleData.loadSample(graph).forkAndForget.runToFuture(monix.execution.Scheduler.global)
        _ <- { //partnerdataset
          import com.github.tototoshi.csv._

          import scala.io.Source

          val csvIri =
            "https://raw.githubusercontent.com/VNG-Realisatie/convenant-gemeenten/master/Documents/Testdata/Partnerdataset_Def.csv"
          val source = Source.fromURL(csvIri)

          implicit object MyFormat extends DefaultCSVFormat {
            override val delimiter = ','
          }
          val reader = CSVReader.open(source)

          val data = reader.allWithHeaders

          val formatter = DateTimeFormatter.ofPattern("M/d/yyyy")

          Observable
            .fromIterable(data)
            .map(_.filter(_._2.nonEmpty))
            .mapEval { record =>
              val ssn = record.get("bsn")
              for {
                person <- ageGraph.nodes.upsert(
                  s"${ageGraph.iri}/person/nl_${ssn.get}",
                  schema.Person)
                _ <- Task.gather {
                  Seq(
                    record
                      .get("geboortedatum")
                      .flatMap { v =>
                        Try(java.time.LocalDate.parse(v, formatter)).toOption
                      }
                      .map { v =>
                        person --- schema.birthDate --> v
                      }
                  ).flatten
                }
              } yield person
            }
            .onErrorHandle { f =>
              scribe.error(f.getMessage); throw f
            }
            .completedL
        }
      } yield ()

    val resetGraphs: Endpoint[IO, String] = get(path("reset")) {
      (for {
        _ <- purge()
        _ <- reset()
      } yield ()).runToFuture(monix.execution.Scheduler.global)

      Ok("resetting now, building graphs...")
    }

    def custompath(name: String) = path(name)
    def purge() = ageTestGraph.purge
    val clearGraphs: Endpoint[IO, String] = get(path("clear")) {
      purge.startAndForget
        .runToFuture(monix.execution.Scheduler.global)
      Ok("clearing now")
    }

    val persist: Endpoint[IO, Unit] = get("persist") {
      scribe.info("persisting all graphs")
      ageTestGraph.persist
      io.finch.NoContent[Unit]
    }
  }

  //  SampleData.loadSample(graph).runSyncUnsafe()(monix.execution.Scheduler.global, CanBlock.permit)
  UtilsApi.reset.runToFuture
  HaalCentraal.backend //eagerly init HaalCentraal

  lazy val create: Endpoint[IO, Node] = {
    import lspace.services.codecs.Decode._

    implicit val bodyJsonldTyped = DecodeJsonLD
      .bodyJsonldTyped(AgeTest.ontology, AgeTest.fromNode)

    implicit val jsonToNodeToT = DecodeJson
      .jsonToNodeToT(AgeTest.ontology, AgeTest.fromNode)

    agetestEndpoint
      .post(agetestEndpoint.body[
        Task[AgeTest],
        lspace.services.codecs.Application.JsonLD :+: Application.Json :+: CNil])
      .mapOutputAsync {
        case task =>
          task
            .flatMap {
              case ageTest: AgeTest
                  if ageTest.result.isDefined || ageTest.id.isDefined =>
                Task.now(NotAcceptable(
                  new Exception("result or id should not yet be defined")))
              case ageTest: AgeTest =>
                val now = Instant.now()
                (for {
                  //                  result <- executeTest(ageTest)
                  result <- HaalCentraal
                    .haalcentraal_get_birthDate_request(ageTest)
                    .onErrorHandle { f =>
                      false
                    }
                  testAsNode <- ageTest
                    .copy(
                      executedOn = Some(now),
                      result = Some(result),
                      id = Some(
                        "http://demo.convenantgemeenten.nl/agetest/" + java.util.UUID
                          .randomUUID()
                          .toString + scala.math.random())
                    )
                    .toNode
                  persistedNode <- ageTestGraph.nodes ++ testAsNode
                } yield {
                  Ok(persistedNode).withHeader("Location" -> persistedNode.iri)
                }).onErrorHandle {
                  case f: Exception => InternalServerError(f)
                }
              case _ =>
                Task.now(NotAcceptable(new Exception("invalid parameters")))
            }
            .to[IO]
      }
  }

  lazy val service: Service[Request, Response] = {

    import lspace.services.codecs.Encode._
    import EncodeJson._
    import EncodeJsonLD._
    import io.finch.Encode._
    import io.finch.ToResponse._
    import io.finch.fs2._

    Bootstrap
      .configure(enableMethodNotAllowed = true,
                 enableUnsupportedMediaType = false)
      .serve[LApplication.JsonLD :+: Application.Json :+: CNil](
        agetestEndpoint.nodeApi.context :+: agetestEndpoint.nodeApi.byId :+: agetestEndpoint.nodeApi.list :+: create :+: agetestEndpoint.nodeApi.removeById)
//        agetestEndpoint.api)
      .serve[LApplication.JsonLD :+: Application.Json :+: CNil](
        agetestEndpoint.graphql)
      .serve[LApplication.JsonLD :+: Application.Json :+: CNil](
        agetestEndpoint.librarian)
      .serve[Text.Plain :+: CNil](
        UtilsApi.clearGraphs :+: UtilsApi.resetGraphs :+: UtilsApi.persist)
      .serve[Text.Html](App.appService.api)
      .toService
  }

  def main(): Unit = {
    val server = Http.server
    //      .configured(Stats(statsReceiver))
      .serve(
        ":8080",
        service
      )

    import lspace.services.util._
    import scala.concurrent.duration._
    onExit {
      println(s"close age-test-server")
      Await.ready(
        Task
          .sequence(
            Seq(
              Task.gatherUnordered(
                Seq(
                  ageGraph.persist,
                  ageTestGraph.persist
                )),
              Task.gatherUnordered(
                Seq(
                  ageGraph.close,
                  ageTestGraph.close
                ))
            ))
          .runToFuture(monix.execution.Scheduler.global),
        20 seconds
      )

      server.close()
    }

    com.twitter.util.Await.ready(adminHttpServer)
  }
}
