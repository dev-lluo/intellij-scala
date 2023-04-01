package org.jetbrains.sbt.project.structure

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.sbt.SbtUtil

import java.io.File
import java.nio.file.Paths
import java.util.regex.{Matcher, Pattern}

object WslUtil {

  private val WINDOWS_FEATURE = {
    val osName = System.getProperty("os.name")
    if (StringUtil.isNotEmpty(osName) && StringUtil.contains(osName.toLowerCase(), "windows")) {
      true
    } else {
      false
    }
  }

  private val DISTRIBUTION_PATTERN = Pattern.compile("^\\\\\\\\wsl\\$\\\\([A-Za-z]+)\\\\$")

  private val WINDOWS_MNT_PATTERN = Pattern.compile("^([A-Za-z]):\\\\$")

  private val WSL_ROOT = Paths.get("/")

  private val WSL_MNT = Paths.get("/mnt")

  def normalizePath(file: String): String = normalizePath(new File(file))

  def normalizePath(file: File): String = {
    if (isOnWSL(file)) {
      val path = file.toPath
      val root = path.getRoot
      val wslPath = WSL_ROOT.resolve(root.relativize(path))
      wslPath.toString.replace('\\', '/')
    } else if (isOnWindowsMnt(file)) {
      val path = file.toPath
      val root = path.getRoot
      val mnt = getWindowsMnt(file).map(_.toLowerCase()).get
      val wslPath = WSL_MNT.resolve(mnt).resolve(root.relativize(path))
      wslPath.toString.replace('\\', '/')
    } else {
      SbtUtil.normalizePath(file)
    }
  }

  def isOnWSL(file: File): Boolean = {
    wslMatch(file) { _ =>
      true
    } {
      false
    }
  }

  def isOnWindowsMnt(file: File): Boolean = {
    windowsMntMatch(file) { _ =>
      true
    } {
      false
    }
  }

  def getWslDistribution(file: File): Option[String] = {
    wslMatch(file) { m =>
      Option(m.group(1))
    } {
      None
    }
  }

  def getWindowsMnt(file: File): Option[String] = {
    windowsMntMatch(file) { m =>
      Option(m.group(1))
    } {
      None
    }
  }

  private def wslMatch[T](file: File)(ifMatch: Matcher => T)(other: => T): T = {
    if (WINDOWS_FEATURE) {
      val rootDesc = file.toPath.getRoot.toString
      if (rootDesc.length > 2 && isSlash(rootDesc.charAt(0)) && isSlash(rootDesc.charAt(1))) {
        val matcher = DISTRIBUTION_PATTERN.matcher(rootDesc)
        if (matcher.matches()) {
          ifMatch(matcher)
        } else {
          other
        }
      } else {
        other
      }
    } else {
      other
    }
  }

  private def windowsMntMatch[T](file: File)(ifMatch: Matcher => T)(other: => T): T = {
    if (WINDOWS_FEATURE) {
      val rootDesc = file.toPath.getRoot.toString
      if (rootDesc.length > 2 && isSlash(rootDesc.charAt(0)) && isSlash(rootDesc.charAt(1))) {
        other
      } else {
        val matcher = WINDOWS_MNT_PATTERN.matcher(rootDesc)
        if (matcher.matches()) {
          ifMatch(matcher)
        } else {
          other
        }
      }
    } else {
      other
    }
  }

  private def isSlash(c: Char) = (c == '\\') || (c == '/')

}
