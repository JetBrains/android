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
import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.android.tools.idea.gradle.project.sync.idea.issues.fetchIdeaProjectForGradleProject
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenFileAtLocationQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.ToggleOfflineModeQuickFix
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.find.FindManager
import com.intellij.find.FindSettings
import com.intellij.find.impl.FindInProjectUtil
import com.intellij.find.impl.FindInProjectUtil.StringUsageTarget
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageSearcher
import com.intellij.usages.UsageViewManager
import com.intellij.util.AdapterProcessor
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler.getErrorLocation
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import java.util.regex.Pattern

class MissingDependencyIssueChecker: GradleIssueChecker {
  private val MISSING_MATCHING_DEPENDENCY_PATTERN = Pattern.compile("Could not find any version that matches (.*)\\.")
  private val MISSING_DEPENDENCY_PATTERN = Pattern.compile("Could not find (.*)\\.")

  override fun check(issueData: GradleIssueData): BuildIssue? {
    val message = GradleExecutionErrorHandler.getRootCauseAndLocation(issueData.error).first.message ?: return null
    if (message.isBlank()) return null
    val buildIssueComposer = getBuildIssueDescription(message, issueData.projectPath) ?: return null
    return buildIssueComposer.composeBuildIssue()
  }

  private fun getBuildIssueDescription(message: String, projectPath: String): BuildIssueComposer? {
    val lineSequence = message.lines()
    val buildIssueComposer = BuildIssueComposer(message)
    val ideaProject = fetchIdeaProjectForGradleProject(projectPath)

    val matcher = MISSING_MATCHING_DEPENDENCY_PATTERN.matcher(lineSequence[0])
    if (matcher.matches()) {
      val dependency = matcher.group(1)
      handleMissingDependency(ideaProject, dependency, buildIssueComposer)
      return buildIssueComposer
    }

    val matcherMissingDependency = MISSING_DEPENDENCY_PATTERN.matcher(lineSequence[0])
    if (matcherMissingDependency.matches() && lineSequence.size > 1 && lineSequence[1].startsWith("Required by:")) {
      val dependency = matcherMissingDependency.group(1)
      if (dependency.isNotEmpty()) {
        getErrorLocation(lineSequence.last())?.also {
          buildIssueComposer.addQuickFix("Open file.",
                                  OpenFileAtLocationQuickFix(FilePosition(File(it.first), it.second - 1, -1)))
        }

        handleMissingDependency(ideaProject, dependency, buildIssueComposer)
        return buildIssueComposer
      }
    }

    for (line in lineSequence) {
      // This happens when Gradle cannot find the Android Gradle plug-in in Maven Central or jcenter.
      val matcherMissingMatching = MISSING_MATCHING_DEPENDENCY_PATTERN.matcher(line)
      if (matcherMissingMatching.matches()) {
        handleMissingDependency(ideaProject, matcherMissingMatching.group(1), buildIssueComposer)
        return buildIssueComposer
      }
    }
    return null
  }

  override fun consumeBuildOutputFailureMessage(message: String,
                                                failureCause: String,
                                                stacktrace: String?,
                                                location: FilePosition?,
                                                parentEventId: Any,
                                                messageConsumer: Consumer<in BuildEvent>): Boolean {
    return MISSING_MATCHING_DEPENDENCY_PATTERN.matcher(failureCause.lines()[0]).matches() ||
           (MISSING_DEPENDENCY_PATTERN.matcher(failureCause.lines()[0]).matches() &&
            failureCause.lines().size > 1 && failureCause.lines()[1].startsWith("Required by:"))
  }
}

class SearchInBuildFilesQuickFix(private val text: String): BuildIssueQuickFix {
  override val id = "search.in.build.files"

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val future = CompletableFuture<Any>()

    invokeLater {
      val findManager = FindManager.getInstance(project)
      val usageViewManager = UsageViewManager.getInstance(project)

      val findModel = findManager.findInProjectModel.clone()
      findModel.stringToFind = text
      findModel.isReplaceState = false
      findModel.fileFilter = SdkConstants.FN_BUILD_GRADLE + "," + SdkConstants.FN_BUILD_GRADLE_KTS

      findManager.findInProjectModel.copyFrom(findModel)
      val findModelCopy = findModel.clone()

      val presentation = FindInProjectUtil.setupViewPresentation(true, findModelCopy)
      val showPanelIfOnlyOneUsage = !FindSettings.getInstance().isSkipResultsWithOneUsage
      val processPresentation =
        FindInProjectUtil.setupProcessPresentation(project, showPanelIfOnlyOneUsage, presentation)

      usageViewManager.searchAndShowUsages(arrayOf(StringUsageTarget(project, findModel)), {
        UsageSearcher { processor ->
          val consumer = AdapterProcessor(processor, UsageInfo2UsageAdapter.CONVERTER)
          FindInProjectUtil.findUsages(findModelCopy, project, consumer, processPresentation)
        }
      }, processPresentation, presentation, null)

      future.complete(null)
    }
    return future
  }
}

private fun handleMissingDependency(project: Project?, dependency: String, buildIssueComposer: BuildIssueComposer) {
  if (project != null && GradleSettings.getInstance(project).isOfflineWork) {
    buildIssueComposer.addQuickFix("Disable Gradle 'offline mode' and sync project", ToggleOfflineModeQuickFix(false))
  }
  buildIssueComposer.addQuickFix("Search in build.gradle files", SearchInBuildFilesQuickFix(dependency))
}