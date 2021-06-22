package org.jetbrains.sbt.codeInspection.typeAnnotation

import com.intellij.openapi.fileTypes.LanguageFileType
import org.jetbrains.plugins.scala.codeInspection.ScalaQuickFixTestBase
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.sbt.{MockSbt_1_0, Sbt}
import org.jetbrains.sbt.codeInspection.SbtDependencyVersionInspection
import org.jetbrains.sbt.language.SbtFileType

class SbtDependencyVersionInspectionTest extends ScalaQuickFixTestBase with MockSbt_1_0{
  override val sbtVersion: Version = Sbt.LatestVersion
  override protected val classOfInspection = classOf[SbtDependencyVersionInspection]
  override protected val description: String = ""
  override protected val fileType: LanguageFileType = SbtFileType

  override protected def descriptionMatches(s: String): Boolean = Option(s).exists(x => x.startsWith("Newer stable version for") && x.endsWith("is available"))

  def testDependencyVersionInspection(): Unit = {
    val text =
      s"""
         |libraryDependencies += "org.scalatest" %% "scalatest" % $START"3.0.7"$END
     """.stripMargin
    val expected =
      s"""
         |libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.8"
     """.stripMargin
    checkTextHasError(text)
    testQuickFix(text, expected, "Update dependency to newer stable version 3.0.8")
  }
}
