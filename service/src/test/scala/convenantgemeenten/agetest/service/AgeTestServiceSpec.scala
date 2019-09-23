package convenantgemeenten.agetest.service

import lspace.services.LService
import org.scalatest.BeforeAndAfter

import scala.concurrent.Future

class AgeTestServiceSpec
    extends lspace.services.LServiceSpec
    with BeforeAndAfter {

  import lspace.codec.argonaut._
  val encoder = lspace.codec.json.jsonld.Encoder(nativeEncoder)

  "The AgeTest service" must {}
}
