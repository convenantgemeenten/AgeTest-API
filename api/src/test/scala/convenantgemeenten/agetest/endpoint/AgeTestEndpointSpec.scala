package convenantgemeenten.agetest.endpoint

import java.time.LocalDate

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response, Status}
import convenantgemeenten.agetest.ns.AgeTest
import io.finch.{Application, Bootstrap, Input}
import lspace.codec
import lspace.codec.ActiveContext
import lspace.codec.argonaut.{nativeDecoder, nativeEncoder}
import lspace.codec.json.jsonld.JsonLDEncoder
import lspace.graphql.QueryResult
import lspace.ns.vocab.schema
import lspace.provider.detached.DetachedGraph
import lspace.provider.mem.MemGraph
import lspace.services.LApplication
import lspace.structure.Graph
import lspace.util.SampleGraph
import monix.eval.Task
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.{BeforeAndAfterAll, FutureOutcome}
import shapeless.{:+:, CNil}

class AgeTestEndpointSpec
    extends AsyncWordSpec
    with Matchers
    with BeforeAndAfterAll {

  import lspace.Implicits.Scheduler.global
  import lspace.encode.EncodeJson
  import lspace.encode.EncodeJson._
  import lspace.encode.EncodeJsonLD
  import lspace.encode.EncodeJsonLD._
  import lspace.services.codecs.Encode._

  lazy val dataGraph: Graph = MemGraph("ApiServiceSpec")
  lazy val testsDataGraph: Graph = MemGraph("ApiServiceSpec")
  implicit val encoderJsonLD = JsonLDEncoder.apply(nativeEncoder)
  implicit val decoderJsonLD =
    lspace.codec.json.jsonld.JsonLDDecoder.apply(DetachedGraph)(nativeDecoder)
  implicit val decoderGraphQL = codec.graphql.Decoder
  import lspace.Implicits.AsyncGuide.guide
  implicit lazy val activeContext = AgeTestEndpoint.activeContext

  val testsEndpoint =
    AgeTestEndpoint(dataGraph, testsDataGraph, "http://example.org/agetests/")

  lazy val service: com.twitter.finagle.Service[Request, Response] = Bootstrap
    .configure(enableMethodNotAllowed = true, enableUnsupportedMediaType = true)
    .serve[LApplication.JsonLD :+: Application.Json :+: CNil](testsEndpoint.api)
    .serve[LApplication.JsonLD :+: Application.Json :+: CNil](
      testsEndpoint.graphql)
    .serve[LApplication.JsonLD :+: Application.Json :+: CNil](
      testsEndpoint.librarian)
    .toService

  lazy val initTask = (for {
    sample <- SampleGraph.loadSocial(dataGraph)
    _ <- for {
      _ <- sample.persons.Yoshio.person --- schema.birthDate --> sample.persons.Yoshio.birthdate.to.value
    } yield ()
  } yield sample).memoizeOnSuccess

  override def withFixture(test: NoArgAsyncTest): FutureOutcome = {
    new FutureOutcome(initTask.runToFuture flatMap { result =>
      super.withFixture(test).toFuture
    })
  }
  "A AgeTestEndpoint" should {
    "test positive for a minimum age of 18 for Yoshio" in {
      (for {
        sample <- initTask
        yoshio = sample.persons.Yoshio.person
        test = AgeTest(yoshio.iri, Some(LocalDate.parse("2019-06-28"))).min(18)
        node <- test.toNode
      } yield {
        val input = Input
          .post("")
          .withBody[LApplication.JsonLD](node)
        testsEndpoint
          .create(input)
          .awaitOutput()
          .map { output =>
            output.isRight shouldBe true
            val response = output.right.get
            response.status shouldBe Status.Ok
            response.value.out(AgeTest.keys.resultBoolean).head shouldBe true
          }
          .getOrElse(fail("endpoint does not match"))
      }).runToFuture
    }
    "test negative for a minimum age of 18 for Yoshio for target date 1855-05-18" in {
      (for {
        sample <- initTask
        yoshio = sample.persons.Yoshio.person
        test = AgeTest(yoshio.iri, LocalDate.parse("1855-05-18")).min(18)
        node <- test.toNode
      } yield {
        val input = Input
          .post("")
          .withBody[LApplication.JsonLD](node)
        testsEndpoint
          .create(input)
          .awaitOutput()
          .map { output =>
            output.isRight shouldBe true
            val response = output.right.get
            response.status shouldBe Status.Ok
            response.value.out(AgeTest.keys.resultBoolean).head shouldBe false
          }
          .getOrElse(fail("endpoint does not match"))
      }).runToFuture
    }
    "test negative for a minimun age of 65 for Yoshio" in {
      (for {
        sample <- initTask
        yoshio = sample.persons.Yoshio.person
        test = AgeTest(yoshio.iri, Some(LocalDate.parse("2019-06-28"))).min(65)
        node <- test.toNode
      } yield {
        val input = Input
          .post("")
          .withBody[LApplication.JsonLD](node)
        testsEndpoint
          .create(input)
          .awaitOutput()
          .map { output =>
            output.isRight shouldBe true
            val response = output.right.get
            response.status shouldBe Status.Ok
            response.value.out(AgeTest.keys.resultBoolean).head shouldBe false
          }
          .getOrElse(fail("endpoint does not match"))
      }).runToFuture
    }
    "test negative for an age between 65 and 75 for Yoshio" in {
      (for {
        sample <- initTask
        yoshio = sample.persons.Yoshio.person
        test = AgeTest(yoshio.iri, Some(LocalDate.parse("2019-06-28")))
          .between(65, 75)
        node <- test.toNode
      } yield {
        val input = Input
          .post("")
          .withBody[LApplication.JsonLD](node)
        testsEndpoint
          .create(input)
          .awaitOutput()
          .map { output =>
            output.isRight shouldBe true
            val response = output.right.get
            response.status shouldBe Status.Ok
            response.value.out(AgeTest.keys.resultBoolean).head shouldBe false
          }
          .getOrElse(fail("endpoint does not match"))
      }).runToFuture
    }
    "test positive for an age between 15 and 25 for Yoshio" in {
      (for {
        sample <- initTask
        yoshio = sample.persons.Yoshio.person
        test = AgeTest(yoshio.iri, LocalDate.parse("2016-06-28")).between(15,
                                                                          25)
        node <- test.toNode
      } yield {
        val input = Input
          .post("")
          .withBody[LApplication.JsonLD](node)
        testsEndpoint
          .create(input)
          .awaitOutput()
          .map { output =>
            output.isRight shouldBe true
            val response = output.right.get
            response.status shouldBe Status.Ok
            response.value.out(AgeTest.keys.resultBoolean).head shouldBe true
          }
          .getOrElse(fail("endpoint does not match"))
      }).runToFuture
    }
  }
  import lspace.services.util._
  "A compiled AgeTestEndpoint" should {
    "test positive for a minimum age of 18 for Yoshio" in {
      (for {
        sample <- initTask
        yoshio = sample.persons.Yoshio.person
        test = AgeTest(yoshio.iri, Some(LocalDate.parse("2019-06-28"))).min(18)
        node <- test.toNode
        input = Input
          .post("/")
          .withBody[LApplication.JsonLD](node)
        _ <- Task
          .fromFuture(service(input.request))
          .flatMap { r =>
            r.status shouldBe Status.Ok
            decoderJsonLD
              .stringToNode(r.contentString)(activeContext)
              .map(_.out(AgeTest.keys.resultBoolean).head shouldBe true)
          }
      } yield succeed).runToFuture
    }
    "test negative for a minimun age of 65 for Yoshio" in {
      (for {
        sample <- initTask
        yoshio = sample.persons.Yoshio.person
        test = AgeTest(yoshio.iri, Some(LocalDate.parse("2019-06-28"))).min(65)
        node <- test.toNode
        input = Input
          .post("/")
          .withBody[LApplication.JsonLD](node)
        _ <- Task
          .fromFuture(service(input.request))
          .flatMap { r =>
            r.status shouldBe Status.Ok
            decoderJsonLD
              .stringToNode(r.contentString)
              .map(_.out(AgeTest.keys.resultBoolean).head shouldBe false)
          }
      } yield succeed).runToFuture
    }
  }
}
