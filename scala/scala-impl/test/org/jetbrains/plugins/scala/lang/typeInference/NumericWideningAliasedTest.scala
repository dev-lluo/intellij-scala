package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class NumericWideningAliasedTest extends ScalaLightCodeInsightFixtureTestAdapter {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_2_13

  def testSCL17848(): Unit = checkTextHasNoErrors(
    """
      |object Test {
      |  type MyId = Long
      |  case class MyClass(myId: MyId)
      |  MyClass(123456) // Red, but compiles fine
      |}""".stripMargin
  )

  def testSCL16379(): Unit = checkTextHasNoErrors(
    """
      |type Id = Long
      |val ln: Long = 42
      |val id: Id = 42
      |""".stripMargin
  )
}
