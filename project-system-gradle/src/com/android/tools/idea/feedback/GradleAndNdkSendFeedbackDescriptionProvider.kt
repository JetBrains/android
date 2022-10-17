/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.feedback

import com.android.SdkConstants
import com.android.annotations.concurrency.Slow
import com.android.repository.api.ProgressIndicator
import com.android.sdklib.internal.project.ProjectProperties
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.actions.SendFeedbackAction
import com.android.tools.idea.actions.SendFeedbackAction.safeCall
import com.android.tools.idea.actions.SendFeedbackDescriptionProvider
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.android.tools.idea.gradle.project.AndroidStudioGradleInstallationManager
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet.Companion.getInstance
import com.android.tools.idea.gradle.util.GradleVersions
import com.android.tools.idea.gradle.util.LocalProperties
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.projectsystem.getAndroidFacets
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.IdeSdks
import com.android.utils.FileUtils
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingAnsiEscapesAwareProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.EnvironmentUtil
import org.jetbrains.android.facet.AndroidFacet
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.util.Properties
import java.util.function.Consumer
import java.util.regex.Pattern

private val LOG = Logger.getInstance(GradleAndNdkSendFeedbackDescriptionProvider::class.java)

class GradleAndNdkSendFeedbackDescriptionProvider : SendFeedbackDescriptionProvider {
  override fun getDescription(project: Project?): Collection<String> {
    val progress: ProgressIndicator = StudioLoggerProgressIndicator(SendFeedbackAction::class.java)
    val sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()

    @Slow
    fun getGradlePluginDetails(): String? {
      val androidPluginInfo = AndroidPluginInfo.find(project ?: return null)
      if (androidPluginInfo != null) {
        val androidPluginVersion = androidPluginInfo.pluginVersion
        if (androidPluginVersion != null) {
          return androidPluginVersion.toString()
        }
      }
      return "(plugin information not found)"
    }

    fun getGradleDetails(): String? {
      val gradleVersion = GradleVersions.getInstance().getGradleVersion(project ?: return null)
      return gradleVersion?.toString() ?: "(gradle version information not found)"
    }

    fun getJdkDetails(): String {
      return if (project == null) getDefaultJdkDetails() else getProjectJdkDetails(project)
    }

    fun item(prefix: String, getter: () -> String?): String? {
      return safeCall { getter() }?.let { "$prefix: $it" }
    }

    fun getNdkDetails(): String = getNdkDetails(project, sdkHandler, progress)
    fun getCMakeDetails(): String = getCMakeDetails(project, sdkHandler, progress)

    return listOfNotNull(
      item("Android Gradle Plugin", ::getGradlePluginDetails),
      item("Gradle", ::getGradleDetails),
      item("Gradle JDK", ::getJdkDetails),
      item("NDK", ::getNdkDetails),
      item("CMake", ::getCMakeDetails),
    )
  }
}

private fun getNdkDetails(
  project: Project?,
  sdkHandler: AndroidSdkHandler,
  progress: ProgressIndicator
): String {
  return buildString {
    project.getAndroidFacets().forEach(Consumer { facet: AndroidFacet ->
      val module = facet.holderModule
      val ndkFacet = getInstance(module)
      val ndkModuleModel = ndkFacet?.ndkModuleModel
      if (ndkModuleModel != null) {
        append("from module: ${ndkModuleModel.ndkModel.ndkVersion}, ")
      }
    })

    // Get version information from all the channels we know, and include it all into the bug to provide
    // the entire context.
    // NDK specified in local.properties (if any)
    if (project != null) {
      try {
        val ndkDir = LocalProperties(project).getProperty(ProjectProperties.PROPERTY_NDK)
        append("from local.properties: ${ndkDir?.let { getNdkVersion(it) } ?: "(not specified)"}, ")
      } catch (e: IOException) {
        LOG.info("Unable to read local.properties file of Project '${project.name}'", e)
      }
    }
    // Latest NDK package in the SDK (if any)
    val p = sdkHandler.getLatestLocalPackageForPrefix(SdkConstants.FD_NDK, null, false, progress)
    append("latest from SDK: ${if (p == null) "(not found)" else getNdkVersion(p.location.toAbsolutePath().toString())}")
  }
}

/**
 * Taken with slight modifications from NdkHelper.getNdkVersion() in android-ndk, but not called directly to
 * avoid dependency of 'android' on 'android-ndk'.
 * TODO: Consider factoring out all version info helpers into a separate module.
 */
private fun getNdkVersion(ndkDir: String): String? {
  val sourcePropertiesFile = File(ndkDir, "source.properties")
  if (sourcePropertiesFile.exists()) {
    //NDK 11+
    var fileInput: InputStream? = null
    return try {
      fileInput = FileInputStream(sourcePropertiesFile)
      val props = Properties()
      props.load(fileInput)
      props.getProperty("Pkg.Revision")
    } catch (e: Exception) {
      LOG.info("Could not read NDK version", e)
      "(unable to read)"
    } finally {
      if (fileInput != null) {
        try {
          fileInput.close()
        } catch (e: IOException) {
          LOG.warn("Failed to close '" + sourcePropertiesFile.path + "'", e)
        }
      }
    }
  }
  val releaseTxtFile = File(ndkDir, "RELEASE.TXT")
  return if (releaseTxtFile.exists()) {
    try {
      // NDK 10
      val content = Files.readAllBytes(releaseTxtFile.toPath())
      String(content).trim { it <= ' ' }
    } catch (e: IOException) {
      LOG.info("Could not read NDK version", e)
      "(unable to read)"
    }
  } else "UNKNOWN"
}

private fun getCMakeDetails(
  project: Project?,
  sdkHandler: AndroidSdkHandler,
  progress: ProgressIndicator
): String {
  return buildString {
    // Get version information from all the channels we know, and include it all into the bug to provide
    // the entire context.
    if (project != null) {
      // CMake specified in local.properties (if any)
      try {
        val cmakeDir = LocalProperties(project).getProperty(ProjectProperties.PROPERTY_CMAKE)
        append(
          "from local.properties: ${
            if (cmakeDir == null) "(not specified)" else runAndGetCMakeVersion(
              getCMakeExecutablePath(
                cmakeDir
              )
            )
          }, "
        )
      } catch (e: IOException) {
        LOG.info("Unable to read local.properties file of Project '${project.name}'", e)
      }
    }
    // Latest CMake package in the SDK (if any)
    val p = sdkHandler.getLatestLocalPackageForPrefix(SdkConstants.FD_CMAKE, null, false, progress)
    append(
      "latest from SDK: ${
        if (p == null) "(not found)"
        else runAndGetCMakeVersion(getCMakeExecutablePath(p.location.toAbsolutePath().toString()))
      }, "
    )
    // CMake from PATH (if any)
    val cmakeBinFromPath = findOnPath("cmake")
    append("from PATH: ${if (cmakeBinFromPath == null) "(not found)" else runAndGetCMakeVersion(cmakeBinFromPath)}")
  }
}

private fun findOnPath(@Suppress("SameParameterValue") executableName: String): String? {
  val path = EnvironmentUtil.getValue("PATH")
  if (path != null) {
    for (dir in StringUtil.tokenize(path, File.pathSeparator)) {
      val candidate = File(dir, executableName)
      if (candidate.canExecute()) {
        return candidate.absolutePath
      }
    }
  }
  return null
}

private fun getCMakeExecutablePath(cmakeDir: String): String {
  val cmakeBinDirectory = FileUtils.join(cmakeDir, "bin")
  val cmakeExecutableName = getCMakeExecutableName()
  val cmakeExecutableFile = File(FileUtils.join(cmakeBinDirectory, cmakeExecutableName))
  return if (!cmakeExecutableFile.exists() || !cmakeExecutableFile.canExecute()) {
    "(binary doesn't exist or is not executable)"
  } else cmakeExecutableFile.absolutePath
}

private val CMAKE_VERSION_PATTERN = Pattern.compile("cmake version\\s+(.*)")

private fun runAndGetCMakeVersion(cmakeExecutableFile: String): String? {
  LOG.info("CMake binary: $cmakeExecutableFile")
  val commandLine = GeneralCommandLine(cmakeExecutableFile)
  commandLine.addParameter("-version")
  return try {
    val process = CapturingAnsiEscapesAwareProcessHandler(commandLine)
    val output = StringBuffer()
    process.addProcessListener(object : ProcessAdapter() {
      override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        output.append(event.text)
        super.onTextAvailable(event, outputType)
      }
    })
    val exitCode = process.runProcess().exitCode
    if (exitCode == 0) {
      val m = CMAKE_VERSION_PATTERN.matcher(output.toString())
      if (m.find()) {
        return m.group(1)
      }
    }
    if (output.isNotEmpty()) output.toString() else "(empty output)"
  } catch (e: ExecutionException) {
    LOG.info("Could not invoke 'cmake -version'", e)
    "(unable to invoke cmake)"
  }
}

private fun getCMakeExecutableName(): String {
  var cmakeExecutableName = "cmake"
  if (SystemInfo.isWindows) {
    cmakeExecutableName += ".exe"
  }
  return cmakeExecutableName
}

private fun getDefaultJdkDetails(): String {
  val jdk = IdeSdks.getInstance().jdk ?: return "(default jdk is not defined)"
  return "(default) " + getJdkVersion(jdk.homePath)
}

private fun getProjectJdkDetails(project: Project): String {
  val basePath = project.basePath ?: return "(cannot find project base path)"
  return getJdkVersion(AndroidStudioGradleInstallationManager.getInstance().getGradleJvmPath(project, basePath))
}

private fun getJdkVersion(jdkPath: String?): String {
  if (jdkPath == null) {
    return "(jdk path not defined)"
  }
  val sdkType = JavaSdk.getInstance()
  return sdkType.getVersionString(jdkPath) ?: return "(jdk version not found)"
}
