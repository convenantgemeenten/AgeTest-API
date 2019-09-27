package convenantgemeenten.agetest.endpoint

import java.time.{Instant, LocalDate}

import cats.effect.IO
import com.softwaremill.sttp.okhttp.monix.OkHttpMonixBackend
import convenantgemeenten.agetest.ns.AgeTest
import io.finch._
import lspace.Label.D._
import lspace._
import lspace.codec._
import lspace.codec.json.jsonld.JsonLDDecoder
import lspace.decode.{DecodeJson, DecodeJsonLD}
import lspace.librarian.task.AsyncGuide
import lspace.ns.vocab.schema
import lspace.services.rest.endpoints.util.MatchParam
import lspace.services.rest.endpoints.{GraphqlApi, LabeledNodeApi, LibrarianApi}
import monix.eval.Task
import monix.execution.Scheduler
import shapeless.{:+:, CNil, HNil}

import scala.collection.immutable.ListMap

object AgeTestEndpoint {
  def apply[Json](ageGraph: Graph, ageTestGraph: Graph, baseUrl: String = "")(
      implicit activeContext: ActiveContext = ActiveContext(),
      decoderJsonLD: JsonLDDecoder[Json],
      ecoderGraphQL: codec.graphql.Decoder,
      guide: AsyncGuide,
      scheduler: Scheduler): AgeTestEndpoint[Json] =
    new AgeTestEndpoint(ageGraph, ageTestGraph, baseUrl)

  lazy val activeContext = ActiveContext(
    `@prefix` = ListMap(
      "person" -> AgeTest.keys.person.iri,
      "minimumAge" -> AgeTest.keys.minimumAge.iri,
      "maximumAge" -> AgeTest.keys.maximumAge.iri,
      "validOn" -> AgeTest.keys.targetDate.iri,
      "executedOn" -> AgeTest.keys.executedOn.iri,
      "result" -> AgeTest.keys.result.iri
    ),
    definitions = Map(
      AgeTest.keys.person.iri -> ActiveProperty(
        `@type` = schema.Person :: Nil,
        property = AgeTest.keys.person)(),
      AgeTest.keys.minimumAge.iri -> ActiveProperty(
        `@type` = `@int` :: Nil,
        property = AgeTest.keys.minimumAge)(),
      AgeTest.keys.maximumAge.iri -> ActiveProperty(
        `@type` = `@int` :: Nil,
        property = AgeTest.keys.maximumAge)(),
      AgeTest.keys.targetDate.iri -> ActiveProperty(
        `@type` = `@date` :: Nil,
        property = AgeTest.keys.targetDate)(),
      AgeTest.keys.executedOn.iri -> ActiveProperty(
        `@type` = `@datetime` :: Nil,
        property = AgeTest.keys.executedOn)(),
      AgeTest.keys.result.iri -> ActiveProperty(
        `@type` = `@boolean` :: Nil,
        property = AgeTest.keys.result)()
    )
  )
}

class AgeTestEndpoint[Json](ageGraph: Graph,
                            ageTestGraph: Graph,
                            baseUrl: String)(
    implicit activeContext: ActiveContext = ActiveContext(),
    decoderJsonLD: JsonLDDecoder[Json],
    ecoderGraphQL: codec.graphql.Decoder,
    guide: AsyncGuide,
    scheduler: Scheduler)
    extends Endpoint.Module[IO] {

  import lspace.services.codecs.Decode._

  lazy val nodeApi = LabeledNodeApi(ageTestGraph, AgeTest.ontology, baseUrl)
  lazy val librarianApi = LibrarianApi(ageTestGraph)
  lazy val graphQLApi = GraphqlApi(ageTestGraph)

  lazy val create: Endpoint[IO, Node] = {
    implicit val bodyJsonldTyped = DecodeJsonLD
      .bodyJsonldTyped(AgeTest.ontology, AgeTest.fromNode)

    implicit val jsonToNodeToT = DecodeJson
      .jsonToNodeToT(AgeTest.ontology, AgeTest.fromNode)

    post(body[
      Task[AgeTest],
      lspace.services.codecs.Application.JsonLD :+: Application.Json :+: CNil])
      .mapOutputAsync {
        case task =>
          task
            .flatMap {
              case ageTest: AgeTest
                  if ageTest.result.isDefined || ageTest.id.isDefined =>
                Task.now(
                  NotAcceptable(
                    new Exception("result or id should not yet be defined")))
              case ageTest: AgeTest
                  if ageTest.minimumAge.isEmpty && ageTest.maximumAge.isEmpty =>
                Task.now(
                  NotAcceptable(new Exception(
                    "minimumAge and/or maximumAge must be defined")))
              case ageTest: AgeTest =>
                val now = Instant.now()
                (for {
                  result <- executeTest(ageTest)
                    .onErrorHandle { f =>
                      false
                    }
                  testAsNode <- ageTest
                    .copy(executedOn = Some(now),
                          result = Some(result),
                          id = Some(
                            baseUrl + java.util.UUID
                              .randomUUID()
                              .toString + scala.math.random()))
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

  def executeTest(ageTest: AgeTest): Task[Boolean] = {
    val targetDate = ageTest.targetDate
      .getOrElse(LocalDate.now())
    g.N
      .hasIri(ageTest.person)
      .has(
        schema.birthDate,
        (ageTest.minimumAge, ageTest.maximumAge) match {
          case (Some(min), Some(max)) =>
            P.between(targetDate
                        .minusYears(max),
                      targetDate
                        .minusYears(min))
          case (Some(min), None) =>
            P.lte(targetDate
              .minusYears(min))
          case (None, Some(max)) =>
            P.gt(targetDate
              .minusYears(max))
        }
      )
      .head()
      .withGraph(ageGraph)
      .headOptionF
      .map(_.isDefined)
  }

  lazy val api = nodeApi.context :+: nodeApi.byId :+: nodeApi.list :+: create :+: nodeApi.removeById
  lazy val graphql = MatchParam[IO]("query") :: graphQLApi.list(
    AgeTest.ontology)
  lazy val librarian = librarianApi.filtered.list(AgeTest.ontology)
}
