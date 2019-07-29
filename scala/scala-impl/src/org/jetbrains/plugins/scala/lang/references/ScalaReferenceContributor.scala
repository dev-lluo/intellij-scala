package org.jetbrains.plugins.scala.lang.references

import java.util
import java.util.Collections

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.{Module, ModuleUtilCore}
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.{Condition, TextRange}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PsiJavaElementPattern
import com.intellij.patterns.PsiJavaPatterns.psiElement
import com.intellij.psi._
import com.intellij.psi.impl.source.resolve.reference.ArbitraryPlaceUrlReferenceProvider
import com.intellij.psi.impl.source.resolve.reference.impl.providers.{FileReference, FileReferenceSet}
import com.intellij.util.ProcessingContext
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScInterpolationPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolated, ScInterpolatedStringLiteral, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScInterpolatedStringPartReference

import scala.collection.mutable.ListBuffer

class ScalaReferenceContributor extends PsiReferenceContributor {

  override def registerReferenceProviders(registrar: PsiReferenceRegistrar) {

    def literalCapture: PsiJavaElementPattern.Capture[ScLiteral] = psiElement(classOf[ScLiteral])

    registrar.registerReferenceProvider(literalCapture, new FilePathReferenceProvider())
    registrar.registerReferenceProvider(literalCapture, new InterpolatedStringReferenceProvider())
    registrar.registerReferenceProvider(literalCapture, new ArbitraryPlaceUrlReferenceProvider())
  }
}

class InterpolatedStringReferenceProvider extends PsiReferenceProvider {
  override def getReferencesByElement(element: PsiElement, context: ProcessingContext): Array[PsiReference] = {
    element match {
      case _: ScInterpolatedStringLiteral => Array.empty
      case l: ScLiteral if (l.isString || l.isMultiLineString) && l.getText.contains("$") =>
        val interpolated = ScalaPsiElementFactory.createExpressionFromText("s" + l.getText, l.getContext)
        interpolated.getChildren.filter {
          case _: ScInterpolatedStringPartReference => false
          case _: ScReferenceExpression => true
          case _ => false
        }.map {
          case ref: ScReferenceExpression =>
            new PsiReference {
              override def getVariants: Array[AnyRef] = Array.empty

              override def getCanonicalText: String = ref.getCanonicalText

              override def getElement: PsiElement = l

              override def isReferenceTo(element: PsiElement): Boolean = ref.isReferenceTo(element)

              override def bindToElement(element: PsiElement): PsiElement = ref

              override def handleElementRename(newElementName: String): PsiElement = ref

              override def isSoft: Boolean = true

              override def getRangeInElement: TextRange = {
                val range = ref.getTextRange
                val startOffset = interpolated.getTextRange.getStartOffset + 1
                new TextRange(range.getStartOffset - startOffset, range.getEndOffset - startOffset)
              }

              override def resolve(): PsiElement = null
            }
        }
      case _ => Array.empty
    }
  }
}

// todo: Copy of the corresponding class from IDEA, changed to use ScLiteral rather than PsiLiteralExpr
private class FilePathReferenceProvider extends PsiReferenceProvider {
  private val LOG: Logger = Logger.getInstance("#org.jetbrains.plugins.scala.lang.references.FilePathReferenceProvider")

  @NotNull def getRoots(thisModule: Module, includingClasses: Boolean): java.util.Collection[PsiFileSystemItem] = {
    if (thisModule == null) return Collections.emptyList[PsiFileSystemItem]
    val modules: java.util.List[Module] = new util.ArrayList[Module]
    modules.add(thisModule)
    var moduleRootManager: ModuleRootManager = ModuleRootManager.getInstance(thisModule)
    ContainerUtil.addAll(modules, moduleRootManager.getDependencies: _*)
    val result: java.util.List[PsiFileSystemItem] = new java.util.ArrayList[PsiFileSystemItem]
    val psiManager: PsiManager = PsiManager.getInstance(thisModule.getProject)
    if (includingClasses) {
      val libraryUrls: Array[VirtualFile] = moduleRootManager.orderEntries.getAllLibrariesAndSdkClassesRoots
      for (file <- libraryUrls) {
        val directory: PsiDirectory = psiManager.findDirectory(file)
        if (directory != null) {
          result.add(directory)
        }
      }
    }
    modules.forEach { module =>
      moduleRootManager = ModuleRootManager.getInstance(module)
      val sourceRoots: Array[VirtualFile] = moduleRootManager.getSourceRoots
      for (root <- sourceRoots) {
        val directory: PsiDirectory = psiManager.findDirectory(root)
        if (directory != null) {
          val aPackage: PsiPackage = JavaDirectoryService.getInstance.getPackage(directory)
          if (aPackage != null && aPackage.name != null) {
            try {
              val createMethod = Class.forName("com.intellij.psi.impl.source.resolve.reference.impl.providers.PackagePrefixFileSystemItemImpl").getMethod("create", classOf[PsiDirectory])
              createMethod.setAccessible(true)
              createMethod.invoke(null, directory)
            } catch {
              case t: Exception  => LOG.warn(t)
            }
          }
          else {
            result.add(directory)
          }
        }
      }
    }
    result
  }

  @NotNull def getReferencesByElement(element: PsiElement, text: String, offset: Int, soft: Boolean): Array[PsiReference] = {
    new FileReferenceSet(text, element, offset, this, true, myEndingSlashNotAllowed) {
      protected override def isSoft: Boolean = soft

      override def isAbsolutePathReference: Boolean = true

      override def couldBeConvertedTo(relative: Boolean): Boolean = !relative

      override def absoluteUrlNeedsStartSlash: Boolean = {
        val s: String = getPathString
        s != null && s.length > 0 && s.charAt(0) == '/'
      }

      @NotNull override def computeDefaultContexts: java.util.Collection[PsiFileSystemItem] = {
        val module: Module = ModuleUtilCore.findModuleForPsiElement(getElement)
        getRoots(module, includingClasses = true)
      }

      override def createFileReference(range: TextRange, index: Int, text: String): FileReference = {
        FilePathReferenceProvider.this.createFileReference(this, range, index, text)
      }

      protected override def getReferenceCompletionFilter: Condition[PsiFileSystemItem] = isPsiElementAccepted(_)

    }.getAllReferences.map(identity)
  }

  override def acceptsTarget(@NotNull target: PsiElement): Boolean = {
    target.isInstanceOf[PsiFileSystemItem]
  }

  protected def isPsiElementAccepted(element: PsiElement): Boolean = {
    !(element.isInstanceOf[PsiJavaFile] && element.isInstanceOf[PsiCompiledElement])
  }

  protected def createFileReference(referenceSet: FileReferenceSet, range: TextRange, index: Int, text: String): FileReference = {
    new FileReference(referenceSet, range, index, text)
  }

  def getReferencesByElement(element: PsiElement, context: ProcessingContext): Array[PsiReference] = {
    element match {
      case interpolated: ScInterpolationPattern =>
        val parts = getStringParts(interpolated)
        val start: Int = interpolated.getTextRange.getStartOffset
        parts.flatMap{ element =>
          val offset = element.getTextRange.getStartOffset - start
          getReferencesByElement(interpolated, element.getText, offset, soft = true)}
      case interpolatedString: ScInterpolatedStringLiteral =>
        val parts = getStringParts(interpolatedString)
        val start: Int = interpolatedString.getTextRange.getStartOffset
        parts.flatMap { element =>
          val offset = element.getTextRange.getStartOffset - start
          getReferencesByElement(interpolatedString, element.getText, offset, soft = true)
        }
      case literal: ScLiteral =>
        literal.getValue match {
          case text: String => getReferencesByElement(element, text, 1, soft = true)
          case _ => PsiReference.EMPTY_ARRAY
        }
      case _ => PsiReference.EMPTY_ARRAY
    }
  }

  private def getStringParts(interpolated: ScInterpolated): Array[PsiElement] = {
    val accepted = List(ScalaTokenTypes.tINTERPOLATED_STRING, ScalaTokenTypes.tINTERPOLATED_MULTILINE_STRING)
    val res = ListBuffer[PsiElement]()
    val children: Array[PsiElement] = interpolated match {
      case ip: ScInterpolationPattern => ip.args.children.toArray
      case sl: ScInterpolatedStringLiteral => Option(sl.getFirstChild.getNextSibling).toArray
    }
    for (child <- children) {
      if (accepted.contains(child.getNode.getElementType))
        res += child
    }
    res.toArray
  }

  private final val myEndingSlashNotAllowed: Boolean = false
}

