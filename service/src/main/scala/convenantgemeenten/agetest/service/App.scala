package convenantgemeenten.agetest.service

import lspace.services.app.JsApp
import lspace.services.rest.endpoints.AppApi
import scalatags.Text.all._
import scalatags.generic.Attr

object App {

  lazy val appService = new AppApi(
    JsApp(
      "app",
      "App",
      Seq(
        //        <!-- Load React. -->
        //  <!-- Note: when deploying, replace "development.js" with "production.min.js". -->
        script(
          src := "https://unpkg.com/react@16/umd/react.development.js",
          //    Attr("integrity") := "sha384-3ceskX3iaEnIogmQchP8opvBy3Mi7Ce34nWjpBIwVTHfGYWQS9jwHDVRnpKKHJg7",
          Attr("crossorigin") := "anonymous"
        ),
        script(
          src := "https://unpkg.com/react-dom@16/umd/react-dom.development.js",
          //    Attr("integrity") := "sha384-3ceskX3iaEnIogmQchP8opvBy3Mi7Ce34nWjpBIwVTHfGYWQS9jwHDVRnpKKHJg7",
          Attr("crossorigin") := "anonymous"
        ),
        script(
          src := "https://code.jquery.com/jquery-3.1.1.min.js",
          Attr("integrity") := "sha384-3ceskX3iaEnIogmQchP8opvBy3Mi7Ce34nWjpBIwVTHfGYWQS9jwHDVRnpKKHJg7",
          Attr("crossorigin") := "anonymous"
        ),
        link(
          rel := "stylesheet",
          href := "https://cdnjs.cloudflare.com/ajax/libs/semantic-ui/2.4.1/semantic.min.css",
          Attr("integrity") := "sha256-9mbkOfVho3ZPXfM7W8sV2SndrGDuh7wuyLjtsWeTI1Q=",
          Attr("crossorigin") := "anonymous"
        ),
        script(
          src := "https://cdnjs.cloudflare.com/ajax/libs/semantic-ui/2.4.1/semantic.min.js",
          Attr("integrity") := "sha256-t8GepnyPmw9t+foMh3mKNvcorqNHamSKtKRxxpUEgFI=",
          Attr("crossorigin") := "anonymous"
        ),
        link(
          rel := "stylesheet",
          href := "https://unpkg.com/leaflet@1.5.1/dist/leaflet.css",
          Attr("integrity") := "sha512-xwE/Az9zrjBIphAcBb3F6JVqxf46+CDLwfLMHloNu6KEQCAWi6HcDUbeOfBIptF7tcCzusKFjFw2yuvEpDL9wQ==",
          Attr("crossorigin") := ""
        ),
        script(
          src := "https://unpkg.com/leaflet@1.5.1/dist/leaflet.js",
          Attr("integrity") := "sha512-GffPMF3RvMeYyc1LWMHtK8EbPv0iNZ8/oTtHPx9/cc2ILxQ+u905qIwdpULaqDkyBKgOaB57QTMg7ztg8Jm2Og==",
          Attr("crossorigin") := ""
        ),
        script(
          src := "https://rawgit.com/mylen/leaflet.TileLayer.WMTS/master/leaflet-tilelayer-wmts.js",
          Attr("integrity") := "sha384-pDiy5rIXkvMKyGwA48rA7lQbCEnP3EorGLDfJnaA9unRESknNE5vzta65dJL5Cbj",
          Attr("crossorigin") := ""
        ),
        script(
          src := "https://cdnjs.cloudflare.com/ajax/libs/moment.js/2.24.0/moment-with-locales.min.js",
          Attr("integrity") := "sha256-AdQN98MVZs44Eq2yTwtoKufhnU+uZ7v2kXnD5vqzZVo=",
          Attr("crossorigin") := "anonymous"
        ),
        script(
          src := "https://cdnjs.cloudflare.com/ajax/libs/moment-timezone/0.5.26/moment-timezone.min.js",
          Attr("integrity") := "sha256-9QeMOhKF5nGXvByLC2BkHmW9JaL6Fn4+YCfhvR0VO2s=",
          Attr("crossorigin") := "anonymous"
        )
      )
    ))
}
