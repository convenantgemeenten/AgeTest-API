package convenantgemeenten.agetest.service

import lspace.services.{LService, LServiceSpec}

class AgeTestServiceSpec
    extends lspace.services.LServiceSpec
    with BeforeAndAfterAll {

  implicit val lservice: LService = AgeTestService.create(80)

  import lspace.codec.argonaut._
  val encoder = lspace.codec.json.jsonld.Encoder(nativeEncoder)

  "The AgeTest service" must {}
}
