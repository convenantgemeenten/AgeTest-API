package convenantgemeenten.agetest.service

import java.time.format.DateTimeFormatter

import cats.effect.IO
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Http, Service}
import com.twitter.server.TwitterServer
import convenantgemeenten.agetest.endpoint._
import io.finch.{Application, Bootstrap, Endpoint}
import lspace._
import lspace.codec.{ActiveContext, jsonld}
import lspace.encode.{EncodeJson, EncodeJsonLD}
import lspace.ns.vocab.schema
import lspace.services.LService
import lspace.services.codecs.{Application => LApplication}
import monix.eval.Task
import monix.execution.Scheduler
import monix.reactive.Observable
import shapeless.{:+:, CNil}

import scala.concurrent.Await
import scala.util.Try

trait AgeTestService extends LService {
  import lspace.codec.argonaut._
  implicit val ec: Scheduler = lspace.Implicits.Scheduler.global

  def ageGraph: Graph
  def ageTestGraph: Graph

  lazy val agetestService =
    AgeTestEndpoint(ageGraph, ageTestGraph, AgeTestEndpoint.activeContext)

  implicit val encoder: lspace.codec.jsonld.Encoder =
    lspace.codec.jsonld.Encoder(nativeEncoder)

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
      purge.forkAndForget
        .runToFuture(monix.execution.Scheduler.global)
      Ok("clearing now")
    }

    val persist: Endpoint[IO, Unit] = get("_persist") {
      scribe.info("persisting all graphs")
      ageTestGraph.persist
      io.finch.NoContent[Unit]
    }
  }

//  SampleData.loadSample(graph).runSyncUnsafe()(monix.execution.Scheduler.global, CanBlock.permit)
  UtilsApi.reset.runToFuture
//  println(SigmaJsVisualizer.visualizeGraph(graph))

  lazy val service: Service[Request, Response] = {

    import lspace.services.codecs.Encode._
    implicit val encoder = jsonld.Encoder.apply(nativeEncoder)
    implicit val activeContext = ActiveContext()

    import EncodeJson._
    import EncodeJsonLD._

    Bootstrap
      .configure(enableMethodNotAllowed = true,
                 enableUnsupportedMediaType = true)
      .serve[LApplication.JsonLD :+: Application.Json :+: CNil](
        agetestService.api)
      .serve[LApplication.JsonLD :+: Application.Json :+: CNil](
        UtilsApi.clearGraphs :+: UtilsApi.resetGraphs :+: UtilsApi.persist)
      .toService
  }
}

object AgeTestService {
  def create(port: Int) = new AgeTestService with TwitterServer {

    lazy val ageGraph: Graph = Graph("ageGraph")
    lazy val ageTestGraph: Graph = Graph("ageTestGraph")

    def main(): Unit = {
      val server = Http.server
      //      .configured(Stats(statsReceiver))
        .serve(
          s":$port",
          service
        )

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
}
