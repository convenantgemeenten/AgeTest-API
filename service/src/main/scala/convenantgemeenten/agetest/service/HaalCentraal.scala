package convenantgemeenten.agetest.service

import argonaut._
import Argonaut._
import java.time.LocalDate

import com.softwaremill.sttp.okhttp.monix.OkHttpMonixBackend
import convenantgemeenten.agetest.ns.AgeTest
import monix.eval.Task

import scala.util.Try

object HaalCentraal {

  case class Datum(datum: Option[LocalDate],
                   jaar: Option[Int],
                   maand: Option[Int],
                   dag: Option[Int])
  object Datum {
    def apply(datum: Option[LocalDate],
              jaar: Option[Int],
              maand: Option[Int],
              dag: Option[Int]): Datum = new Datum(datum, jaar, maand, dag)

    def unapply(arg: Datum)
      : Option[(Option[LocalDate], Option[Int], Option[Int], Option[Int])] = ???

    implicit def decodeLocalDate: DecodeJson[LocalDate] =
      optionDecoder(
        json => json.string.flatMap(s => Try(LocalDate.parse(s)).toOption),
        "???")
    implicit def encodeLocalDate: EncodeJson[LocalDate] =
      EncodeJson[LocalDate](date => date.toString.asJson)
    implicit def DatumDecodeJson: CodecJson[Datum] =
      casecodec4(Datum.apply, Datum.unapply)("datum", "jaar", "maand", "dag")
  }
  case class Plaats(code: Option[String], omschrijving: Option[String])
  object Plaats {
    def apply(code: Option[String], omschrijving: Option[String]): Plaats =
      new Plaats(code, omschrijving)

    def unapply(arg: Plaats): Option[(Option[String], Option[String])] = ???

    implicit def PlaatsDecodeJson: argonaut.CodecJson[Plaats] =
      casecodec2(Plaats.apply, Plaats.unapply)("code", "omschrijving")
  }
  case class Land(code: Option[String], omschrijving: Option[String])
  object Land {
    def apply(code: Option[String], omschrijving: Option[String]): Land =
      new Land(code, omschrijving)

    def unapply(arg: Land): Option[(Option[String], Option[String])] = ???

    implicit def LandDecodeJson: argonaut.CodecJson[Land] =
      casecodec2(Land.apply, Land.unapply)("code", "omschrijving")
  }
  case class Geboorte(datum: Option[Datum],
                      plaats: Option[Plaats],
                      land: Option[Land])
  object Geboorte {
    def apply(datum: Option[Datum],
              plaats: Option[Plaats],
              land: Option[Land]): Geboorte = new Geboorte(datum, plaats, land)

    def unapply(
        arg: Geboorte): Option[(Option[Datum], Option[Plaats], Option[Land])] =
      ???

    import Datum._
    import Plaats._
    import Land._

    implicit def GeboorteCodecJson: CodecJson[Geboorte] =
      casecodec3(Geboorte.apply, Geboorte.unapply)("datum", "plaats", "land")
  }

  import Geboorte._

  def parse(json: Json): Option[Geboorte] = json.jdecode[Geboorte].toOption
  def parseDatum(json: Json): Option[Datum] = json.jdecode[Datum].toOption
  def parsePlaats(json: Json): Option[Plaats] = json.jdecode[Plaats].toOption
  def parseLand(json: Json): Option[Land] = json.jdecode[Land].toOption

  import com.softwaremill.sttp._

  implicit val backend = OkHttpMonixBackend()

  val hcConfig = HCConfig.hcConfig

  def haalcentraal_get_birthDate_request(ageTest: AgeTest): Task[Boolean] = {
    val bsn = ageTest.person.reverse.takeWhile(_ != '_').reverse
    val request = sttp
      .get(uri"${hcConfig.url}/ingeschrevenpersonen/$bsn/?fields=geboorte")
      .header("x-api-key", hcConfig.xApiKey.value, true)
      .header("Accept", "application/json", true)

    request
      .send()
      .flatMap { response =>
        response.body match {
          case Left(error) => Task.raiseError(new Exception(error))
          case Right(body) => {
            (_root_.argonaut.Parse
              .parse(body) match {
              case Right(json) =>
                json.obj
                  .flatMap(_.toMap.get("geboorte"))
                  .map(Task.now)
                  .getOrElse(Task.raiseError(
                    new Exception("does not contain field 'geboorte'")))
              case Left(error) =>
                Task.raiseError(new Exception(error))
            }).map(HaalCentraal.parse)
          }.map { geboorte =>
            val targetDate = ageTest.targetDate.getOrElse(LocalDate.now())
            (ageTest.minimumAge, ageTest.maximumAge) match {
              case (Some(min), Some(max)) =>
                geboorte.exists(
                  geboorte =>
                    geboorte.datum.exists(
                      datum =>
                        datum.datum.exists(
                          date =>
                            (date
                              .plusYears(min)
                              .isEqual(targetDate) || date
                              .plusYears(min)
                              .isBefore(targetDate)) &&
                              date
                                .plusYears(max)
                                .isAfter(targetDate))))
              case (Some(min), None) =>
                geboorte.exists(
                  geboorte =>
                    geboorte.datum.exists(
                      datum =>
                        datum.datum.exists(
                          date =>
                            date
                              .plusYears(min)
                              .isEqual(targetDate) || date
                              .plusYears(min)
                              .isBefore(targetDate))))
              case (None, Some(max)) =>
                geboorte.exists(
                  geboorte =>
                    geboorte.datum.exists(
                      datum =>
                        datum.datum.exists(date =>
                          date
                            .plusYears(max)
                            .isAfter(targetDate))))
              case _ => false
            }

          }
        }
      }
  }
}
