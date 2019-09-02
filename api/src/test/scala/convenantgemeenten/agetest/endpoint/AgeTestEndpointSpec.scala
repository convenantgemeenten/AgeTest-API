package convenantgemeenten.agetest.endpoint

import java.time.LocalDate

import com.twitter.finagle.http.Status
import convenantgemeenten.agetest.ns.AgeTest
import io.finch.Input
import lspace.ns.vocab.schema
import lspace.provider.mem.MemGraph
import lspace.services.codecs.{Application => LApplication}
import lspace.structure.Graph
import lspace.util.SampleGraph
import monix.eval.Task
import org.scalatest.{AsyncWordSpec, BeforeAndAfterAll, FutureOutcome, Matchers}

class AgeTestEndpointSpec
    extends AsyncWordSpec
    with Matchers
    with BeforeAndAfterAll {

  import lspace.Implicits.Scheduler.global
  import lspace.encode.EncodeJsonLD._
  import lspace.services.codecs.Encode._

  lazy val ageGraph: Graph = MemGraph("ApiServiceSpec")
  lazy val ageTestGraph: Graph = MemGraph("ApiServiceSpec")
  implicit val nencoder = lspace.codec.argonaut.NativeTypeEncoder
  implicit val encoder = lspace.codec.jsonld.Encoder(nencoder)
  implicit val ndecoder = lspace.codec.argonaut.NativeTypeDecoder

  implicit lazy val activeContext = AgeTestEndpoint.activeContext

  val ageService = AgeTestEndpoint(ageGraph, ageTestGraph)

  lazy val initTask = (for {
    sample <- SampleGraph.loadSocial(ageGraph)
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
        test = AgeTest(yoshio.iri, 18, Some(LocalDate.parse("2019-06-28")))
        node <- test.toNode
      } yield {
        val input = Input
          .post("")
          .withBody[LApplication.JsonLD](node)
        ageService
          .age(input)
          .awaitOutput()
          .map { output =>
            output.isRight shouldBe true
            val response = output.right.get
            response.status shouldBe Status.Ok
            response.value.tail.get.head.get shouldBe true
          }
          .getOrElse(fail("endpoint does not match"))
      }).runToFuture
    }
    "test positive for a minimum age of 18 for Yoshio via query parameters" in {
      (for {
        sample <- initTask
        yoshio = sample.persons.Yoshio.person
        test = AgeTest(yoshio.iri, 18, Some(LocalDate.parse("2019-06-28")))
        node <- test.toNode
      } yield {
        val input = Input
          .get(s"?id=123&minimumAge=18&targetDate=2019-06-28")
          .withBody[LApplication.JsonLD](node)
        ageService
          .age(input)
          .awaitOutput()
          .map { output =>
            output.isRight shouldBe true
            val response = output.right.get
            response.status shouldBe Status.Ok
            response.value.head.get shouldBe true
          }
          .getOrElse(fail("endpoint does not match"))
      }).runToFuture
    }
    "test negative for a minimum age of 18 for Yoshio for target date 1855-05-18" in {
      (for {
        sample <- initTask
        yoshio = sample.persons.Yoshio.person
        test = AgeTest(yoshio.iri, 18, Some(LocalDate.parse("1855-05-18")))
        node <- test.toNode
      } yield {
        val input = Input
          .post("")
          .withBody[LApplication.JsonLD](node)
        ageService
          .age(input)
          .awaitOutput()
          .map { output =>
            output.isRight shouldBe true
            val response = output.right.get
            response.status shouldBe Status.Ok
            response.value.tail.get.head.get shouldBe false
          }
          .getOrElse(fail("endpoint does not match"))
      }).runToFuture
    }
    "test negative for a minimun age of 65 for Yoshio" in {
      (for {
        sample <- initTask
        yoshio = sample.persons.Yoshio.person
        test = AgeTest(yoshio.iri, 65, Some(LocalDate.parse("2019-06-28")))
        node <- test.toNode
      } yield {
        val input = Input
          .post("")
          .withBody[LApplication.JsonLD](node)
        ageService
          .age(input)
          .awaitOutput()
          .map { output =>
            output.isRight shouldBe true
            val response = output.right.get
            response.status shouldBe Status.Ok
            response.value.tail.get.head.get shouldBe false
          }
          .getOrElse(fail("endpoint does not match"))
      }).runToFuture
    }
  }
  "A compiled AgeTestEndpoint" should {
    "test positive for a minimum age of 18 for Yoshio" in {
      (for {
        sample <- initTask
        yoshio = sample.persons.Yoshio.person
        test = AgeTest(yoshio.iri, 18, Some(LocalDate.parse("2019-06-28")))
        node <- test.toNode
        input = Input
          .post("")
          .withBody[LApplication.JsonLD](node)
        _ <- Task.from(
          ageService
            .compiled(input.request)
            .map {
              case (t, Left(e)) => fail()
              case (t, Right(r)) =>
                r.status shouldBe Status.Ok
                r.contentString shouldBe "true"
            })
      } yield succeed).runToFuture
    }
    "test negative for a minimun age of 65 for Yoshio" in {
      (for {
        sample <- initTask
        yoshio = sample.persons.Yoshio.person
        test = AgeTest(yoshio.iri, 65, Some(LocalDate.parse("2019-06-28")))
        node <- test.toNode
        input = Input
          .post("")
          .withBody[LApplication.JsonLD](node)
        _ <- Task.from(
          ageService
            .compiled(input.request)
            .map {
              case (t, Left(e)) => fail()
              case (t, Right(r)) =>
                r.status shouldBe Status.Ok
                r.contentString shouldBe "false"
            })
      } yield succeed).runToFuture
    }
  }
}
