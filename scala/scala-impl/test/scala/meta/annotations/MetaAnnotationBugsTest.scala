package scala.meta.annotations

import scala.collection.JavaConverters._

import com.intellij.lang.annotation.HighlightSeverity
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTypeDefinition}
import org.junit.Assert
import org.junit.Assert.{assertEquals, assertFalse, assertTrue, fail}

class MetaAnnotationBugsTest extends MetaAnnotationTestBase {

  def testSCL10965(): Unit = {
    import scala.meta.intellij.psiExt._
    compileMetaSource()
    myFixture.configureByText("Foo.scala",
      """
        |@repro sealed trait FooOp[A]
        |object FooOp {
        |  final case class StringOp(string: String) extends FooOp[String]
        |  final case class AOp[A](a: A) extends FooOp[A]
        |}
      """.stripMargin
    )
    val expectedExpansion =
      """{
        |  sealed trait FooOp[A]
        |  object FooOp {
        |    trait ForF[F[_]] {
        |      def stringOp(string: String): F[String]
        |      def aOp[A](a: A): F[A]
        |    }
        |    final case class StringOp(string: String) extends FooOp[String]()
        |    final case class AOp[A](a: A) extends FooOp[A]()
        |  }
        |}""".stripMargin
    myFixture.findClass("FooOp").asInstanceOf[ScTypeDefinition].getMetaExpansion match {
      case Right(tree)                      => assertEquals(expectedExpansion, tree.toString())
      case Left(reason) if reason.nonEmpty  => fail(reason)
      case Left("")                         => fail("Expansion was empty - did annotation even run?")
    }
  }

  def testSCL11099(): Unit = {
    compileMetaSource()
    val code =
      """
        |object App {
        |  @poly def <caret>fooOpToId[A](fooOp: FooOp[A]): Id[A] = fooOp match {
        |    case StringOp(string) => Right(string)
        |    case AOp(a) => Left(())
        |  }
        |}""".stripMargin
    val expansion =
      """
        |val fooOpToId: _root_.cats.arrow.FunctionK[FooOp, Id] = new _root_.cats.arrow.FunctionK[FooOp, Id] {
        |  def apply[A](fooOp: FooOp[A]): Id[A] = fooOp match {
        |    case StringOp(string) =>
        |      Right(string)
        |    case AOp(a) =>
        |      Left(())
        |  }
        |}""".stripMargin.trim
    checkExpansionEquals(code, expansion)
  }

  def testSCL12032(): Unit = {
    compileMetaSource(
      """
        |import scala.meta._
        |class a extends scala.annotation.StaticAnnotation {
        |  inline def apply(defn: Any): Any = meta {
        |    defn match {
        |      case q"..$mods def $name[..$tparams](...$paramss): $tpeopt = $expr" =>
        |        q"..$mods def $name[..$tparams](...$paramss): $tpeopt = $expr"
        |
        |      case _ => abort("@a can only be applied to method")
        |    }
        |   }
        | }
      """.stripMargin)

    val codeBar =
      """
        |class A {
        |  @a def foo() = "not important"
        |  @a protected def bar<caret>() = "not important"
        |}
      """.stripMargin
    val expectedBar = "protected def bar() = \"not important\""
    checkExpansionEquals(codeBar, expectedBar)

    val codeFoo =
      """
        |class A {
        |  @a def foo<caret>() = "not important"
        |  @a protected def bar() = "not important"
        |}
      """.stripMargin
    val expectedFoo = "def foo() = \"not important\""
    checkExpansionEquals(codeFoo, expectedFoo)
  }

  def testSCL11952(): Unit = {
    import org.jetbrains.plugins.scala.lang.psi.types.result._
    def fail(msg:String): Nothing = {Assert.fail(msg); ???}
    compileMetaSource()
    myFixture.configureByText(s"${getTestName(false)}.scala",
    """
      |@SCL11952
      |class Foo
      |
      |object Bar {
      |  def fooA: Foo.A = ???
      |}
    """.stripMargin)

    val foo = myFixture.findClass("Foo").asInstanceOf[ScClass]
    val bar = myFixture.findClass("Bar$").asInstanceOf[ScObject]
    val fooType = foo.fakeCompanionModule
      .getOrElse(fail("No companion generated by annotation"))
      .members
      .find(_.getName == "foo")
      .map(_.asInstanceOf[ScFunction].returnType)
      .getOrElse(fail("Method not generated by annotation")) match {
      case Right(res) => res
      case Failure(cause)  => fail(s"Failed to infer generated method type: $cause")
    }

    val fooBarAType = bar.members
      .find(_.getName == "fooA").get
      .asInstanceOf[ScFunction].returnType match {
      case Right(res) => res
      case Failure(cause)  => fail(s"Failed to infer generated method type: $cause")
    }

    assertTrue("Generated type not equal to specified", fooType.equiv(fooBarAType))
  }

  def testSCL12104(): Unit = {
    compileMetaSource(
      """
        |import scala.meta._
        |class AnnotationWithType[SomeType] extends scala.annotation.StaticAnnotation {
        |  inline def apply(defn: Any): Any = meta { defn }
        |}
      """.stripMargin
    )
    myFixture.configureByText(s"$testClassName.scala",
    s"""
      |@AnnotationWithType[Unit]
      |class $testClassName
    """.stripMargin
    )
    checkExpandsNoError()
  }

  // SCL-12385 Macro fails to expand if embedded inside an object
  def testSCL12385(): Unit = {
    compileMetaSource(
      s"""
        |import scala.meta._
        |object Foo {
        |  class Argument(arg: Int) extends scala.annotation.StaticAnnotation {
        |    inline def apply(defn: Any): Any = meta {  q"class $testClassName { def bar = 42 }"  }
        |  }
        |}
      """.stripMargin)

    createFile(
      s"""
        |@Foo.Argument(2) class $testClassName
      """.stripMargin
    )
    checkExpandsNoError()
  }

  def testSCL12371(): Unit = {
    val newMethodName = "bar"
    compileAnnotBody(s"""q"class $testClassName { def $newMethodName = 42 }" """)
    createFile(
      s"""
        |object Bla {
        |  @$annotName class $testClassName
        |  val ex = new $testClassName
        |  val ret = ex.$newMethodName<caret> //cannot resolve symbol bar (error does not appear when macro is expanded)
        |}
      """.stripMargin
    )
    assertTrue("Synthetic method not injected", testClass.members.exists(_.getName == newMethodName))
  }

  def testSCL12509(): Unit = {
    compileAnnotBody(
      """
        |val q"class $className" = defn
        |q"final class $className(val value : Int) extends AnyVal"
      """.stripMargin.trim
    )
    createFile(s"@$annotName class $testClassName\nnew $testClassName(42).value<caret>")
    val errors = myFixture.doHighlighting(HighlightSeverity.ERROR)
    val errorStr = ScalaBundle.message("value.class.can.have.only.one.parameter")
    assertFalse("Value class constructor not resolved", !errors.isEmpty && errors.asScala.exists(_.getDescription == errorStr))
    assertTrue("Value class field not resolved", myFixture.getElementAtCaret != null)
  }

  // Redundant parentheses in macro expansion
  def testSCL12465(): Unit = {
    compileAnnotBody("defn")
    checkExpansionEquals(s"@$annotName trait<caret> Foo extends Bar", "trait Foo extends Bar")
  }

  // Synthetic import statements are not processed
  def testSCL12360(): Unit = {
    compileAnnotBody(
      s"""val q"class $$name { ..$$stats }" = defn
         |q${tq}class $$name {
         |import Foo._
         |..$$stats
         |}$tq
         |""".stripMargin)

    createFile(
      s"""
        |object Foo {
        |  def foo() = 42
        |}
        |
        |@$annotName
        |class Bar {
        |  //import Foo._
        |  foo<caret>()
        |}
      """.stripMargin
    )

    assertTrue("Imported member doesn't resolve", refAtCaret.bind().isDefined)
  }

  // scala.meta macro expansion fails when pattern matching on annotation constructor with Symbol*
  def testSCL11401(): Unit = {
    compileMetaSource(
      s"""import scala.meta._
        |class $annotName(fields: scala.Symbol*) extends scala.annotation.StaticAnnotation {
        |  inline def apply(defn: Any): Any = meta {
        |    this match { case q"new $$_(..$$xs)" => xs.map { case ctor"$$_($${Lit(x: String)})" => x } }
        |    defn
        |  }
        |}""".stripMargin
    )
    createFile(s"@$annotName('abc) class $testClassName")
    val errors = myFixture.doHighlighting(HighlightSeverity.ERROR)
    assert(errors.isEmpty, s"Symbol in annotation causes error: ${errors.get(0)}")
  }

  // Type parameters resolve to both synthetic and physical elements if target is present in both
  def testSCL12582(): Unit = {
    compileAnnotBody("defn")
    createFile(s"@$annotName class $testClassName[T] { type K = T<caret> }")
    val resClass = refAtCaret.multiResolveScala(false)
    assertEquals("Reference should resolve to single element in class", 1, resClass.size)
    createFile(s"@$annotName trait $testClassName[T] { type K = T<caret> }")
    val resTrait = refAtCaret.multiResolveScala(false)
    assertEquals("Reference should resolve to single element in trait", 1, resTrait.size)
  }

  def testSCL12582_2(): Unit = {
    compileAnnotBody(s""" q"trait $testClassName[F[_]]" """)
    createFile(
      s"""
         |@$annotName
         |trait $testClassName
         |class Foo extends $testClassName[Foo]
      """.stripMargin
    )

    checkNoErrorHighlights("Wrong number of type parameters.")
  }

}
