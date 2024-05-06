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

import com.android.annotations.concurrency.Slow
import com.android.repository.Revision
import com.android.sdklib.repository.meta.DetailsTypes
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.android.tools.idea.gradle.project.sync.idea.issues.fetchIdeaProjectForGradleProject
import com.android.tools.idea.gradle.project.sync.issues.SyncFailureUsageReporter
import com.android.tools.idea.gradle.project.sync.issues.processor.FixBuildToolsProcessor
import com.android.tools.idea.gradle.project.sync.quickFixes.InstallBuildToolsQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenFileAtLocationQuickFix
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.projectsystem.gradle.GradleHolderProjectPath
import com.android.tools.idea.projectsystem.gradle.resolveIn
import com.android.tools.idea.sdk.AndroidSdks
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import java.util.regex.Pattern

/**
 * This IssueChecker is for olg AGP version where having an old build tools version ends up in a sync issue. For newer AGP (tested for 3.1.0
 * and above, a low build tools version will be ignored and the default minimum version required by the SDK handler will be chosen.
 */
class SdkBuildToolsTooLowIssueChecker: GradleIssueChecker {
  private val SDK_BUILD_TOOLS_TOO_LOW_PATTERN = Pattern.compile(
    "The SDK Build Tools revision \\((.*)\\) is too low for project '(.*)'. Minimum required is (.*)")

  @Slow
  override fun check(issueData: GradleIssueData): BuildIssue? {
    val message = GradleExecutionErrorHandler.getRootCauseAndLocation(issueData.error).first.message ?: return null
    if (message.isEmpty()) return null
    val composer = getBuildIssueDescriptionAndQuickFixes(message, issueData.projectPath) ?: return null

    // Log metrics.
    SyncFailureUsageReporter.getInstance().collectFailure(issueData.projectPath, GradleSyncFailure.SDK_BUILD_TOOLS_TOO_LOW)

    return composer.composeBuildIssue()
  }

  @Slow
  private fun getBuildIssueDescriptionAndQuickFixes(message: String, projectPath: String): BuildIssueComposer? {
    val matcher = SDK_BUILD_TOOLS_TOO_LOW_PATTERN.matcher(message)
    if (!matcher.matches()) return null

    val buildIssueComposer = BuildIssueComposer(message)
    val gradlePath = matcher.group(2)
    val minVersion = matcher.group(3)

    // Get IDEA project that contains the current Gradle project instance.
    val ideaProject = fetchIdeaProjectForGradleProject(projectPath) ?: return buildIssueComposer

    // TODO(b/149203281): Fix support for composite projects.
    val modules = listOfNotNull(GradleHolderProjectPath(FileUtil.toSystemIndependentName(projectPath), gradlePath).resolveIn(ideaProject))
    val buildFiles = listOfNotNull(if (modules.isEmpty()) null else GradleProjectSystemUtil.getGradleBuildFile(
      modules[0]
    )
    )

    val sdkHandler = AndroidSdks.getInstance().tryToChooseAndroidSdk()?.sdkHandler
    if (sdkHandler != null) {
      val progress = StudioLoggerProgressIndicator(SdkBuildToolsTooLowIssueChecker::class.java)
      val packages = sdkHandler.getSdkManager(progress).packages
      val buildTool = packages.localPackages[DetailsTypes.getBuildToolsPath(Revision.parseRevision(minVersion))]
      if (buildTool == null) {
        val linkMessage = "Install Build Tools $minVersion " +
                          if (buildFiles.isNotEmpty()) ", update version in build file and sync project" else " and sync project"

        buildIssueComposer.addQuickFix(linkMessage,
                                InstallBuildToolsQuickFix(minVersion, buildFiles, doesAndroidGradlePluginPackageBuildTools(ideaProject)))
      }
      else if(buildFiles.isNotEmpty()) {
        val removeBuildTools = doesAndroidGradlePluginPackageBuildTools(ideaProject)
        buildIssueComposer.addQuickFix("${if (removeBuildTools) "Remove" else "Update"} Build Tools version and sync project",
                                FixBuildToolsVersionQuickFix(minVersion, buildFiles, removeBuildTools))
      }
    }

    if (buildFiles.isNotEmpty()) {
      buildIssueComposer.addQuickFix("Open file.", OpenFileAtLocationQuickFix(FilePosition(File(buildFiles[0].path), -1, -1)))
    }
    return buildIssueComposer
  }

  override fun consumeBuildOutputFailureMessage(message: String,
                                                failureCause: String,
                                                stacktrace: String?,
                                                location: FilePosition?,
                                                parentEventId: Any,
                                                messageConsumer: Consumer<in BuildEvent>): Boolean {
    return SDK_BUILD_TOOLS_TOO_LOW_PATTERN.matcher(failureCause).matches()
  }
}

@Slow
fun doesAndroidGradlePluginPackageBuildTools(project: Project): Boolean {
  val pluginInfo = AndroidPluginInfo.find(project)
  if (pluginInfo != null) {
    val agpVersion = pluginInfo.pluginVersion
    if (agpVersion != null && !agpVersion.isAtLeast(3, 0, 0)) {
      return false
    }
  }
  return true
}

class FixBuildToolsVersionQuickFix(
  private val version: String,
  private val buildFiles: List<VirtualFile>,
  private val removeBuildTools: Boolean): BuildIssueQuickFix {
  override val id = "fix.build.tools.version"

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val future = CompletableFuture<Any>()
    invokeLater {
      val processor = FixBuildToolsProcessor(project, buildFiles, version, true, removeBuildTools)
      processor.setPreviewUsages(true)
      processor.run()
      future.complete(null)
    }
    return future
  }
}