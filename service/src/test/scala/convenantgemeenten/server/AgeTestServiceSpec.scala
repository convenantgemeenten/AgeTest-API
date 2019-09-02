package convenantgemeenten.server

import com.twitter.finagle.http.Status
import io.finch.Input
import io.finch.Application
import lspace.codec.ActiveContext
import lspace.ns.vocab.schema
import lspace.provider.detached.DetachedGraph
import lspace.services.{LService, LServiceSpec}
import monix.eval.Task
import org.scalatest.BeforeAndAfterAll

class AgeTestServiceSpec extends LServiceSpec with BeforeAndAfterAll {

  implicit val lservice: LService = AgeTestService.create(80)

  import lspace.Implicits.Scheduler.global
  import lspace.codec.argonaut._
  val encoder: lspace.codec.jsonld.Encoder = lspace.codec.jsonld.Encoder(nativeEncoder)
  import encoder._
  import lspace.encode.EncodeJson._
  import lspace.services.codecs.Encode._

  "The AgeTest service" must {}
}
