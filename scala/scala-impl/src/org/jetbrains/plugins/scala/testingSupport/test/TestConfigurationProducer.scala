package org.jetbrains.plugins.scala
package testingSupport.test

import com.intellij.execution.{Location, RunnerAndConfigurationSettings}
import com.intellij.execution.actions.{ConfigurationContext, ConfigurationFromContext, RunConfigurationProducer}
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.junit.InheritorChooser
import com.intellij.ide.util.PsiClassListCellRenderer
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.module.Module
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.{Condition, Ref}
import com.intellij.psi._
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.ui.components.JBList
import javax.swing.ListCellRenderer
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.testingSupport.test.testdata.{ClassTestData, SingleTestData}
import org.jetbrains.plugins.scala.util.UIFreezingGuard

import scala.collection.JavaConverters._

/**
 * @author Roman.Shein
 *         Date: 11.12.13
 */
abstract class TestConfigurationProducer(configurationType: ConfigurationType)
  extends RunConfigurationProducer[AbstractTestRunConfiguration](configurationType)
    with AbstractTestConfigurationProducer {

  protected def isObjectInheritor(clazz: ScTypeDefinition, fqn: String): Boolean =
    clazz.elementScope.getCachedObject(fqn)
      .exists {
        ScalaPsiUtil.isInheritorDeep(clazz, _)
      }

  final def getLocationClassAndTest(location: Location[_ <: PsiElement]): (ScTypeDefinition, String) = {
    val timeoutMs =
      if (ApplicationManager.getApplication.isDispatchThread) UIFreezingGuard.resolveTimeoutMs else -1

    UIFreezingGuard.withTimeout(timeoutMs, getLocationClassAndTestImpl(location), (null, null))(location.getProject)
  }


  protected def getLocationClassAndTestImpl(location: Location[_ <: PsiElement]): (ScTypeDefinition, String)

  override def setupConfigurationFromContext(configuration: AbstractTestRunConfiguration,
                                             context: ConfigurationContext,
                                             sourceElement: Ref[PsiElement]): Boolean = {
    def setup(testElement: PsiElement, confSettings: RunnerAndConfigurationSettings) = {
      val configWithModule = configuration.clone.asInstanceOf[AbstractTestRunConfiguration]
      val cfg = confSettings.getConfiguration.asInstanceOf[AbstractTestRunConfiguration]
      configWithModule.setModule(cfg.getModule)

      val runIsPossible = isRunPossibleFor(configWithModule, testElement)
      if (runIsPossible) {
        sourceElement.set(testElement)
        configuration.setGeneratedName(cfg.suggestedName)
        configuration.setFileOutputPath(cfg.getOutputFilePath)
        configuration.setModule(cfg.getModule)
        configuration.setName(cfg.getName)
        configuration.setNameChangedByUser(!cfg.isGeneratedName)
        configuration.setSaveOutputToFile(cfg.isSaveOutputToFile)
        configuration.setShowConsoleOnStdErr(cfg.isShowConsoleOnStdErr)
        configuration.setShowConsoleOnStdOut(cfg.isShowConsoleOnStdOut)
        configuration.testConfigurationData = cfg.testConfigurationData
      }
      runIsPossible
    }

    if (sourceElement.isNull) {
      false
    } else {
      createConfigurationByElement(context.getLocation, context) match {
        case Some((testElement, confSettings)) if testElement != null && confSettings != null =>
          setup(testElement, confSettings)
        case _ =>
          false
      }
    }
  }

  override def onFirstRun(configuration: ConfigurationFromContext, context: ConfigurationContext, startRunnable: Runnable): Unit = {
    configuration.getConfiguration match {
      case config: AbstractTestRunConfiguration =>
        config.testConfigurationData match {
          case testData: ClassTestData =>
            val testClass = testData.getClassPathClazz
            if (!(config.isInvalidSuite(testClass) &&
              new InheritorChooser() {
                override def runMethodInAbstractClass(context: ConfigurationContext, performRunnable: Runnable,
                                                      psiMethod: PsiMethod, containingClass: PsiClass,
                                                      acceptAbstractCondition: Condition[PsiClass]): Boolean = {
                  //TODO this is mostly copy-paste from InheritorChooser; get rid of this once we support pattern test runs
                  if (containingClass == null) return false
                  val classes = ClassInheritorsSearch
                    .search(containingClass)
                    .asScala
                    .filterNot{config.isInvalidSuite}
                    .toList

                  if (classes.isEmpty) return false

                  if (classes.size == 1) {
                    runForClass(classes.head, psiMethod, context, performRunnable)
                    return true
                  }

                  val fileEditor = PlatformDataKeys.FILE_EDITOR.getData(context.getDataContext)
                  fileEditor match {
                    case editor: TextEditor =>
                      val document = editor.getEditor.getDocument
                      val containingFile = PsiDocumentManager.getInstance(context.getProject).getPsiFile(document)
                      containingFile match {
                        case owner: PsiClassOwner =>
                          val psiClasses = owner.getClasses
                          psiClasses.filter(classes.contains(_))
                          if (psiClasses.size == 1) {
                            runForClass(psiClasses.head, psiMethod, context, performRunnable)
                            return true
                          }
                        case _ =>
                      }
                    case _ =>
                  }
                  val renderer = new PsiClassListCellRenderer()
                  val classesSorted = classes.sorted(Ordering.comparatorToOrdering(renderer.getComparator))
                  val jbList = new JBList(classesSorted:_*)
                  //scala type system gets confused because someone forgot generics in PsiElementListCellRenderer definition
                  jbList.setCellRenderer(renderer.asInstanceOf[ListCellRenderer[PsiClass]])
                  JBPopupFactory.getInstance().createListPopupBuilder(jbList).setTitle("Choose executable classes to run " +
                    (if (psiMethod != null) psiMethod.getName else containingClass.getName)).setMovable(false).
                    setResizable(false).setRequestFocus(true).setItemChoosenCallback(new Runnable() {
                    override def run(): Unit = {
                      val values = jbList.getSelectedValuesList
                      if (values == null) return
                      if (values.size == 1) {
                        runForClass(values.get(0), psiMethod, context, performRunnable)
                      }
                    }
                  }).createPopup().showInBestPositionFor(context.getDataContext)
                  true
                }

                override protected def runForClass(aClass: PsiClass, psiMethod: PsiMethod, context: ConfigurationContext,
                                                   performRunnable: Runnable) {
                  testData.setTestClassPath(aClass.getQualifiedName)
                  config.setName(StringUtil.getShortName(aClass.getQualifiedName) + (testData match {
                    case single: SingleTestData => "." + single.getTestName
                    case _ => ""
                  }))
                  Option(ScalaPsiUtil.getModule(aClass)) foreach config.setModule
                  performRunnable.run()
                }
              }.runMethodInAbstractClass(context, startRunnable, null, testClass))) super.onFirstRun(configuration, context, startRunnable)
          case _ => startRunnable.run()
        }
      case _ => startRunnable.run()
    }
  }

  override def isConfigurationFromContext(configuration: AbstractTestRunConfiguration, context: ConfigurationContext): Boolean = {
    //TODO: implement me properly
    val runnerClassName = configuration.mainClass

    if (runnerClassName != null && runnerClassName == configuration.mainClass) {
      val configurationModule: Module = configuration.getConfigurationModule.getModule
      if (context.getLocation != null) {
        isConfigurationByLocation(configuration, context.getLocation)
      } else {
        (context.getModule == configurationModule ||
                context.getRunManager.getConfigurationTemplate(getConfigurationFactory).getConfiguration.asInstanceOf[AbstractTestRunConfiguration]
                        .getConfigurationModule.getModule == configurationModule) && !configuration.testConfigurationData.isInstanceOf[ClassTestData]
      }
    } else false
  }

  protected def isRunPossibleFor(configuration: AbstractTestRunConfiguration, testElement: PsiElement): Boolean = {

    testElement match {
      case cl: PsiClass =>
        configuration.isValidSuite(cl) ||
          ClassInheritorsSearch
            .search(cl)
            .asScala
            .exists(configuration.isValidSuite)
      case _ => true
    }
  }
}
