/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.errors

import com.android.SdkConstants
import com.android.tools.idea.IdeInfo
import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.android.tools.idea.gradle.project.sync.idea.issues.updateUsageTracker
import com.android.tools.idea.io.FilePaths
import com.android.tools.idea.projectsystem.AndroidProjectRootUtil
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.IdeSdks
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SystemProperties.getUserName
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler
import java.io.File
import java.util.function.Consumer
import java.util.regex.Pattern

open class FailedToParseSdkIssueChecker: GradleIssueChecker {
  private val FAILED_TO_PARSE = "failed to parse SDK"
  private val EXCEPTION_TRACE_PATTERN = Pattern.compile("Caused by: java.lang.RuntimeException(.*)")

  override fun check(issueData: GradleIssueData): BuildIssue? {
    val rootCause = GradleExecutionErrorHandler.getRootCauseAndLocation(issueData.error).first
    val message = rootCause.message ?: return null
    if (rootCause !is RuntimeException || message.isBlank() || !message.contains(FAILED_TO_PARSE)) return null

    // Log metrics.
    invokeLater {
      updateUsageTracker(issueData.projectPath, GradleSyncFailure.FAILED_TO_PARSE_SDK)
    }

    val buildIssueComposer = BuildIssueComposer(message)
    val pathOfBrokenSdk = findPathOfSdkWithoutAddonsFolder(issueData.projectPath)
                          ?: return buildIssueComposer.apply { addDescription("The Android SDK may be missing the directory 'add-ons'.")
                          }.composeBuildIssue()

    buildIssueComposer.addDescription(
      "The directory '${SdkConstants.FD_ADDONS}', in the Android SDK at '${pathOfBrokenSdk.path}', is either missing or empty")

    if (!pathOfBrokenSdk.canWrite()) {
      buildIssueComposer.addDescription("Current user ('${getUserName()}') does not have write access to the SDK directory.")
    }

    return buildIssueComposer.composeBuildIssue()
  }

  override fun consumeBuildOutputFailureMessage(message: String,
                                                failureCause: String,
                                                stacktrace: String?,
                                                location: FilePosition?,
                                                parentEventId: Any,
                                                messageConsumer: Consumer<in BuildEvent>): Boolean {
    return stacktrace != null && EXCEPTION_TRACE_PATTERN.matcher(stacktrace).find() && failureCause.contains(FAILED_TO_PARSE)
  }

  @VisibleForTesting
  open fun findPathOfSdkWithoutAddonsFolder(projectPath: String) : File? {
    val androidSdk = AndroidSdks.getInstance()
    if (IdeInfo.getInstance().isAndroidStudio) {
      val sdkPath = IdeSdks.getInstance().androidSdkPath
      if (sdkPath != null && isMissingAddonsFolder(sdkPath)) {
        return sdkPath
      }
    }
    else {
      // Try find project form the Gradle path
      // TODO(151215857) : Update here when it's possible to use the Gradle projectPath in sync services.
      var projectFound = false
      var modules: Array<Module>? = null
      for (project in ProjectManager.getInstance().openProjects) {
        modules = ModuleManager.getInstance(project).modules
        for (module in modules) {
          if (AndroidProjectRootUtil.getModuleDirPath(module) == projectPath) {
            projectFound = true
            break
          }
        }
        if (projectFound) break
      }

      if (!projectFound || modules == null) return null

      for (module in modules) {
        val moduleSdk = ModuleRootManager.getInstance(module).sdk ?: continue
        if (androidSdk.isAndroidSdk(moduleSdk)) {
          val homePath = moduleSdk.homePath ?: continue
          val sdkHomePath = FilePaths.stringToFile(homePath)
          // sdkHomePath is never null as homePath isn't at this stage.
          if (isMissingAddonsFolder(sdkHomePath!!)) {
            return sdkHomePath
          }
        }
      }
    }
    return null
  }

  private fun isMissingAddonsFolder(sdkHomePath: File): Boolean {
    val addonsFolder = File(sdkHomePath, SdkConstants.FD_ADDONS)
    return !addonsFolder.isDirectory || FileUtil.notNullize(addonsFolder.listFiles()).isEmpty()
  }
}