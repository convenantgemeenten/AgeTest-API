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
      labels = Map("nl" -> "Leeftijdstoets"),
      comments = Map(
        "nl" -> "Een leeftijdstoets is een toetst of een persoon aan bepaalde leeftijdskenmerken voldoet."),
      `@extends` = () => Test :: Nil
    ) {
  object keys extends Test.Properties {
    object person
        extends PropertyDef(ontology.iri + "/person",
                            label = "person",
                            `@range` = () => schema.Person.ontology :: Nil)

    object minimumAge
        extends PropertyDef(
          ontology.iri + "/minimumAge",
          label = "minimumAge",
          `@extends` = () =>
            Property("https://ns.convenantgemeenten.nl/minimumAge") :: Nil,
          `@range` = () => `@int` :: Nil)
    lazy val minimumAgeInt = minimumAge as `@int`

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
            () => Property("https://ns.convenantgemeenten.nl/result") :: Nil,
          `@range` = () => Label.D.`@boolean` :: Nil
        )
    lazy val resultBoolean
      : TypedProperty[Boolean] = result as Label.D.`@boolean`
  }
  override lazy val properties
    : List[Property] = keys.person.property :: keys.minimumAge.property :: keys.targetDate.property :: keys.result.property :: schema.Thing.properties
  trait Properties {
    lazy val person = keys.person
    lazy val result = keys.result
    lazy val minimumAge = keys.minimumAge
    lazy val targetDate = keys.targetDate
    lazy val targetDateDate = keys.targetDateDate
    lazy val resultBoolean = keys.resultBoolean
  }

  def fromNode(node: Node): AgeTest = {
    AgeTest(
      node.outE(keys.person.property).head.to.iri,
      node.out(keys.minimumAgeInt).head,
      node.out(keys.targetDateDate).headOption,
      node.out(keys.executedOn as lspace.Label.D.`@datetime`).headOption,
      node.out(keys.resultBoolean).headOption,
      if (node.iri.nonEmpty) Some(node.iri) else None
    )
  }

  implicit def toNode(cc: AgeTest): Task[Node] = {
    for {
      node <- cc.id
        .map(DetachedGraph.nodes.upsert(_, ontology))
        .getOrElse(DetachedGraph.nodes.create(ontology))
      person <- DetachedGraph.nodes.upsert(cc.person, Set[String]())
      _ <- node --- keys.person --> person
      _ <- node --- keys.minimumAge --> cc.minimumAge
      _ <- cc.targetDate
        .map(node --- keys.targetDate --> _)
        .getOrElse(Task.unit)
      _ <- cc.executedOn
        .map(node --- keys.executedOn --> _)
        .getOrElse(Task.unit)
      _ <- cc.result
        .map(result => node --- keys.result --> result)
        .getOrElse(Task.unit)
    } yield node
  }
}
case class AgeTest(person: String,
                   minimumAge: Int,
                   targetDate: Option[LocalDate] = None,
                   executedOn: Option[Instant] = None,
                   result: Option[Boolean] = None,
                   id: Option[String] = None) {
  lazy val toNode: Task[Node] = this
}
