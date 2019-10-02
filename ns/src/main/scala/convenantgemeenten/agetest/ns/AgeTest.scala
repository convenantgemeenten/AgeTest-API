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
        "An age test is an assertion whether a subject satisfies certain age constraints.",
      labels = Map("nl" -> "Leeftijdstoets"),
      comments = Map(
        "nl" -> "Een leeftijdstoets is een toetst of een subject aan bepaalde leeftijdskenmerken voldoet."),
      `@extends` = () => Test :: Nil
    ) {
  object keys extends Test.Properties {
    object subject
        extends PropertyDef(ontology.iri + "/subject",
                            label = "subject",
                            `@range` = () => schema.Thing.ontology :: Nil)

    object minimumAge
        extends PropertyDef(
          ontology.iri + "/minimumAge",
          label = "minimumAge",
          `@extends` = () =>
            Property("https://ns.convenantgemeenten.nl/minimumAge") :: Nil,
          `@range` = () => `@int` :: Nil)
    lazy val minimumAgeInt = minimumAge as `@int`

    object maximumAge
        extends PropertyDef(
          ontology.iri + "/maximumAge",
          label = "maximumAge",
          `@extends` = () =>
            Property("https://ns.convenantgemeenten.nl/maximumAge") :: Nil,
          `@range` = () => `@int` :: Nil)
    lazy val maximumAgeInt = maximumAge as `@int`

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
    : List[Property] = keys.subject.property :: keys.minimumAge.property :: keys.maximumAge.property :: keys.targetDate.property :: keys.result.property :: Test.properties
  trait Properties extends Test.Properties {
    lazy val person = keys.subject
    lazy val result = keys.result
    lazy val minimumAge = keys.minimumAge
    lazy val maximumAge = keys.maximumAge
    lazy val targetDate = keys.targetDate
    lazy val targetDateDate = keys.targetDateDate
    lazy val resultBoolean = keys.resultBoolean
  }

  def fromNode(node: Node): AgeTest = {
    AgeTest(
      node.outE(keys.subject.property).head.to.iri,
      node.out(keys.minimumAgeInt).headOption,
      node.out(keys.maximumAgeInt).headOption,
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
      subject <- DetachedGraph.nodes.upsert(cc.subject, Set[String]())
      _ <- node --- keys.subject --> subject
      _ <- cc.minimumAge
        .map(node --- keys.minimumAge --> _)
        .getOrElse(Task.unit)
      _ <- cc.maximumAge
        .map(node --- keys.maximumAge --> _)
        .getOrElse(Task.unit)
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

  trait AgeTestFactory {
    def min(age: Int): AgeTest
    def max(age: Int): AgeTest
    def between(minAge: Int, maxAge: Int): AgeTest
  }
  def apply(person: String,
            targetDate: Option[LocalDate] = None): AgeTestFactory =
    new AgeTestFactory {
      def min(age: Int): AgeTest =
        new AgeTest(person, Some(age), None, targetDate)
      def max(age: Int): AgeTest =
        new AgeTest(person, None, Some(age), targetDate)
      def between(minAge: Int, maxAge: Int): AgeTest =
        new AgeTest(person, Some(minAge), Some(maxAge), targetDate)
    }
  def apply(person: String, targetDate: LocalDate): AgeTestFactory =
    apply(person, Some(targetDate))

  def apply(subject: String,
            minimumAge: Option[Int],
            maximumAge: Option[Int],
            targetDate: Option[LocalDate],
            executedOn: Option[Instant],
            result: Option[Boolean],
            id: Option[String]): AgeTest =
    new AgeTest(subject,
                minimumAge,
                maximumAge,
                targetDate,
                executedOn,
                result,
                id)
}
case class AgeTest private (subject: String,
                            minimumAge: Option[Int],
                            maximumAge: Option[Int],
                            targetDate: Option[LocalDate] = None,
                            executedOn: Option[Instant] = None,
                            result: Option[Boolean] = None,
                            id: Option[String] = None) {
  lazy val toNode: Task[Node] = this
}
