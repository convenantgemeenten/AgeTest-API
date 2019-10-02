import com.raquo.laminar.api.L._

object Main extends App {

  val openapiViewer =
    """
      |<!DOCTYPE html>
      |<html>
      |  <head>
      |    <title>ReDoc</title>
      |    <!-- needed for adaptive design -->
      |    <meta charset="utf-8"/>
      |    <meta name="viewport" content="width=device-width, initial-scale=1">
      |    <link href="https://fonts.googleapis.com/css?family=Montserrat:300,400,700|Roboto:300,400,700" rel="stylesheet">
      |
      |    <!--
      |    ReDoc doesn't change outer page styles
      |    -->
      |    <style>
      |      body {
      |        margin: 0;
      |        padding: 0;
      |      }
      |    </style>
      |  </head>
      |  <body>
      |    <redoc spec-url='https://raw.githubusercontent.com/convenantgemeenten/AgeTest-API/master/openapi.yaml'></redoc>
      |    <script src="https://cdn.jsdelivr.net/npm/redoc@next/bundles/redoc.standalone.js"> </script>
      |  </body>
      |</html>
      |""".stripMargin

  private val root = div(position.absolute, height := "100%", width := "100%")
  org.scalajs.dom.document.body.appendChild(root.ref)
  val wrapper =
    iframe(height := "100%", width := "100%")
  render(root.ref, wrapper)
  wrapper.ref.contentWindow.document.open("text/html", "replace")
  wrapper.ref.contentWindow.document.write(openapiViewer)
  wrapper.ref.contentWindow.document.close()
}
