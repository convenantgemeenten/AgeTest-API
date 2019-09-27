package convenantgemeenten.agetest.service

object HCConfig {
  import pureconfig._
  import pureconfig.generic.auto._

  private val baseconfigspath = Option(System.getenv("AGETEST_CONFIGS_PATH"))
  val hcConfig = (if (baseconfigspath.exists(_.nonEmpty)) {
                    ConfigSource
                      .file(baseconfigspath + "secrets.conf")
                      .optional
                      .withFallback(
                        ConfigSource.file(baseconfigspath + "application.conf"))
                      .optional
                      .withFallback(ConfigSource.resources("secrets.conf"))
                      .optional
                      .withFallback(ConfigSource.resources("application.conf"))
                  } else {
                    ConfigSource
                      .resources("secrets.conf")
                      .optional
                      .withFallback(ConfigSource.default)
                  })
    .at("haal-centraal")
    .loadOrThrow[HCConfig]

  println(hcConfig)
}
case class Sensitive(value: String) extends AnyVal {
  override def toString: String = "MASKED"
}
case class HCConfig(url: String, xApiKey: Sensitive)
