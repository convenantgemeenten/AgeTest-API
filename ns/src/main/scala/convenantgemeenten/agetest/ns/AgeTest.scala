package convenantgemeenten.agetest.ns

import java.time.{Instant, LocalDate}

import convenantgemeenten.ns.Test
import lspace.Label
import lspace.Label.D._
import lspace.ns.vocab.schema
import lspace.provider.detached.DetachedGraph
import lspace.structure._
import monix.eval.Task

object AgeTest
    extends OntologyDef(
      "https://ns.convenantgemeenten.nl/AgeTest",
      label = "Age test",
      comment =
        "An age test is an assertion whether a person satisfies certain age constraints.",
      labels = Map("nl" -> "Partner test"),
      comments = Map(
        "nl" -> "Een leeftijdstest is een toetst of een persoon aan bepaalde leeftijdskenmerken voldoet.")
    ) {
  object keys extends Test.Properties {
    object person
        extends PropertyDef(ontology.iri + "/person",
                            label = "person",
                            `@range` = () => schema.Person.ontology :: Nil)

    lazy val requiredMinAge = schema.requiredMinAge
    lazy val requiredMinAgeInt = requiredMinAge as `@int`

    object targetDate
        extends PropertyDef("https://ns.convenantgemeenten.nl/targetDate",
                            label = "targetDate",
                            `@range` = () => `@date` :: Nil)
    lazy val targetDateDate = targetDate as `@date`

    object result
        extends PropertyDef(
          ontology.iri + "/result",
          label = "result",
          `@extends` =
            () =>
              Property.properties.getOrCreate("https://schema.org/result",
                                              Set()) :: Nil,
          `@range` = () => Label.D.`@boolean` :: Nil
        )
    lazy val resultBoolean
      : TypedProperty[Boolean] = result as Label.D.`@boolean`
  }
  override lazy val properties
    : List[Property] = keys.person.property :: keys.requiredMinAge.property :: keys.targetDate.property :: keys.result.property :: schema.Thing.properties
  trait Properties {
    lazy val person = keys.person
    lazy val result = keys.result
    lazy val requiredMinAge = keys.requiredMinAge
    lazy val targetDate = keys.targetDate
    lazy val targetDateDate = keys.targetDateDate
    lazy val resultBoolean = keys.resultBoolean
  }

  def fromNode(node: Node): AgeTest = {
    AgeTest(
      node.outE(keys.person.property).head.to.iri,
      node.out(keys.requiredMinAgeInt).head,
      node.out(keys.targetDateDate).headOption,
      node.out(keys.executedOn as lspace.Label.D.`@datetime`).headOption,
      node.out(keys.resultBoolean).headOption
    )
  }

  implicit def toNode(cc: AgeTest): Task[Node] = {
    for {
      node <- DetachedGraph.nodes.create(ontology)
      person <- DetachedGraph.nodes.upsert(cc.person, Set[String]())
      _ <- node --- keys.person.property --> person
      _ <- node --- keys.requiredMinAge.property --> cc.requiredMinAge
      _ <- cc.targetDate
        .map(node --- keys.targetDate.property --> _)
        .getOrElse(Task.unit)
      _ <- cc.executedOn
        .map(node --- keys.executedOn.property --> _)
        .getOrElse(Task.unit)
      _ <- cc.result
        .map(result => node --- keys.result --> result)
        .getOrElse(Task.unit)
    } yield node
  }
}
case class AgeTest(person: String,
                   requiredMinAge: Int,
                   targetDate: Option[LocalDate] = None,
                   executedOn: Option[Instant] = None,
                   result: Option[Boolean] = None) {
  lazy val toNode: Task[Node] = this
}
