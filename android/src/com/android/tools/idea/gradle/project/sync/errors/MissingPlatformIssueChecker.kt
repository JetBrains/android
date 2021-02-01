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

import com.android.sdklib.AndroidTargetHash
import com.android.sdklib.AndroidVersion
import com.android.sdklib.repository.meta.DetailsTypes
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.android.tools.idea.gradle.project.sync.idea.issues.updateUsageTracker
import com.android.tools.idea.projectsystem.AndroidProjectRootUtil
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.facet.FacetManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import java.util.regex.Pattern

private val MISSING_PLATFORM_PATTERNS = listOf(
  Pattern.compile("(Cause: )?(F|f)ailed to find target with hash string '(.*)' in: (.*)"),
  Pattern.compile("(Cause: )?(F|f)ailed to find target (.*) : (.*)"),
  Pattern.compile("(Cause: )?(F|f)ailed to find target (.*)"))

private val ILLEGAL_STATE_TRACE_PATTERN = Pattern.compile("Caused by: java.lang.IllegalStateException(.*)")
private val EXTERNAL_SYSTEM_EXCEPTION_PATTERN =
  Pattern.compile("Caused by: com.intellij.openapi.externalSystem.model.ExternalSystemException(.*)")

class MissingPlatformIssueChecker: GradleIssueChecker {

  override fun check(issueData: GradleIssueData): BuildIssue? {
    val rootCause  = GradleExecutionErrorHandler.getRootCauseAndLocation(issueData.error).first
    val message = rootCause.message ?: return null
    val missingPlatform = getMissingPlatform(message)
    if (message.isBlank() || missingPlatform == null ||
        (rootCause !is IllegalStateException) && (rootCause !is ExternalSystemException)) return null

    // Log metrics.
    invokeLater {
      updateUsageTracker(issueData.projectPath, GradleSyncFailure.MISSING_ANDROID_PLATFORM)
    }
    val buildIssueComposer = BuildIssueComposer(message)

    // Get quickFixes.
    val sdkHandler = AndroidSdks.getInstance().tryToChooseAndroidSdk()?.sdkHandler
    if (sdkHandler != null) {
      val version = AndroidTargetHash.getPlatformVersion(missingPlatform)
      if (version != null) {
        val logger = StudioLoggerProgressIndicator(this::class.java)
        // Get the details about the cause of the error.
        val causes = sdkHandler.getAndroidTargetManager(logger).getErrorForPackage(DetailsTypes.getPlatformPath(version))
        buildIssueComposer.addDescription(if (causes != null) "possible cause: \n ${causes}" else "")
        buildIssueComposer.addQuickFix("Install missing platform(s) and sync project", InstallPlatformQuickFix(listOf(version)))
      }
    }
    if (buildIssueComposer.issueQuickFixes.isEmpty()) {
      // We weren't able to offer quickFixes to install the concerned platform.
      // So check if the project has an Android facet to open the SDK manager (Android facet has a reference to Android DSK manager).

      // First get the module from the projectPath.
      var projectFound = false
      // TODO(151215857) : Update here when it's possible to use the Gradle projectPath in sync services.
      for (project in ProjectManager.getInstance().openProjects) {
        for (module in ModuleManager.getInstance(project).modules) {
          if (AndroidProjectRootUtil.getModuleDirPath(module) == issueData.projectPath) {
            projectFound = true
            // If module is found, fetch Android facet.
            val facets = FacetManager.getInstance(module).getFacetsByType(AndroidFacet.ID)
            if (facets.isNotEmpty()) {
              // If android facet found, offer to open Android SDK manager.
              buildIssueComposer.addQuickFix("Open Android SDK Manager", OpenAndroidSdkManagerQuickFix())
            }
            break
          }
        }
        if (projectFound) break
      }
    }

    return buildIssueComposer.composeBuildIssue()
  }

  override fun consumeBuildOutputFailureMessage(message: String,
                                                failureCause: String,
                                                stacktrace: String?,
                                                location: FilePosition?,
                                                parentEventId: Any,
                                                messageConsumer: Consumer<in BuildEvent>): Boolean {
    return stacktrace != null && (ILLEGAL_STATE_TRACE_PATTERN.matcher(stacktrace).find() ||
                                  EXTERNAL_SYSTEM_EXCEPTION_PATTERN.matcher(stacktrace).find()) && getMissingPlatform(failureCause) != null
  }
}

class InstallPlatformQuickFix(private val androidVersions: List<AndroidVersion>): BuildIssueQuickFix {
  override val id = "install.android.platform"

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val future = CompletableFuture<Any>()
    val platforms = mutableListOf<String>()
    invokeLater {
      for (version in androidVersions) {
        platforms.add(DetailsTypes.getPlatformPath(version))
      }
      val dialog = SdkQuickfixUtils.createDialogForPaths(project, platforms)
      if (dialog != null && dialog.showAndGet()) {
        GradleSyncInvoker.getInstance().requestProjectSync(project, GradleSyncStats.Trigger.TRIGGER_QF_PLATFORM_INSTALLED)
      }
      future.complete(null)
    }
    return future
  }
}

class OpenAndroidSdkManagerQuickFix: BuildIssueQuickFix {
  override val id = "open.android.sdk.manager"

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    SdkQuickfixUtils.showAndroidSdkManager()
    return CompletableFuture.completedFuture<Any>(null)
  }
}

@VisibleForTesting
fun getMissingPlatform(message: String): String? {
  val firstLine = message.lines()[0]
  for (pattern in MISSING_PLATFORM_PATTERNS) {
    val matcher = pattern.matcher(firstLine)
    if (matcher.matches()) {
      return matcher.group(3)
    }
  }
  return null
}