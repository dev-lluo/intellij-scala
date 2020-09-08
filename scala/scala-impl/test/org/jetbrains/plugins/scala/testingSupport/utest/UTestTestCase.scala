package org.jetbrains.plugins.scala.testingSupport.utest

import com.intellij.execution.actions.RunConfigurationProducer
import org.jetbrains.plugins.scala.testingSupport.ScalaTestingTestCase
import org.jetbrains.plugins.scala.testingSupport.test.utest.UTestConfigurationProducer
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestConfigurationProducer, TestConfigurationUtil}

/**
  * @author Roman.Shein
  * @since 13.05.2015.
  */
abstract class UTestTestCase extends ScalaTestingTestCase {
  override protected val configurationProducer: AbstractTestConfigurationProducer[_] =
    RunConfigurationProducer.getInstance(classOf[UTestConfigurationProducer])

  protected val testSuiteSecondPrefix = "import utest.framework.TestSuite"

  // TestRunnerUtil.unescapeTestNam is not used in UTestRunner
  override protected def unescapeTestName(str: String): String = str
}
