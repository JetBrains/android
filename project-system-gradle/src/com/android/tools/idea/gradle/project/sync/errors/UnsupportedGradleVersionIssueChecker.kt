/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.android.tools.idea.gradle.project.sync.idea.issues.DescribedBuildIssueQuickFix
import com.android.tools.idea.gradle.project.sync.idea.issues.fetchIdeaProjectForGradleProject
import com.android.tools.idea.gradle.project.sync.issues.SyncFailureUsageReporter
import com.android.tools.idea.gradle.project.sync.quickFixes.CreateGradleWrapperQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenFileAtLocationQuickFix
import com.android.tools.idea.gradle.project.sync.requestProjectSync
import com.android.tools.idea.gradle.util.GradleProjectSettingsFinder
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil
import com.android.tools.idea.gradle.util.GradleWrapper
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.gradle.GradleManager
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler.getRootCauseAndLocation
import org.jetbrains.plugins.gradle.settings.DistributionType
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import java.util.regex.Pattern
import java.util.regex.Pattern.DOTALL

class UnsupportedGradleVersionIssueChecker: GradleIssueChecker {
  /** This message is generated in AGP by VersionCheckPlugin */
  private val UNSUPPORTED_GRADLE_VERSION_PATTERN = Pattern.compile("Minimum supported Gradle version is (.*)\\. Current version is.*", DOTALL)

  override fun check(issueData: GradleIssueData): BuildIssue? {
    val error = getRootCauseAndLocation(issueData.error).first

    // If formatMessage returns null then we can't handle this error
    val message = formatMessage(error.message) ?: return null

    // Log metrics.
    SyncFailureUsageReporter.getInstance().collectFailure(issueData.projectPath, GradleSyncFailure.UNSUPPORTED_GRADLE_VERSION)
    val buildIssueComposer = BuildIssueComposer(message)

    // Get QuickFixes.
    val ideaProject = fetchIdeaProjectForGradleProject(issueData.projectPath)

    if (ideaProject != null) {
      val gradleWrapper = GradleWrapper.find(ideaProject)
      val gradleVersion = getSupportedGradleVersion(message)
      if (gradleWrapper != null) {
        // It's likely that we need to fix the model version as well.
        buildIssueComposer.addQuickFix(FixGradleVersionInWrapperQuickFix(gradleWrapper, gradleVersion))
        val propertiesFile = gradleWrapper.propertiesFilePath
        if (propertiesFile.exists()) {
          buildIssueComposer.addQuickFix(
            "Open Gradle wrapper properties", OpenFileAtLocationQuickFix(FilePosition(propertiesFile, -1, -1)))
        }
      }
      else {
        val gradleProjectSettings = GradleProjectSettingsFinder.getInstance().findGradleProjectSettings(ideaProject)
        if (gradleProjectSettings != null && gradleProjectSettings.distributionType == DistributionType.LOCAL) {
          buildIssueComposer.addQuickFix("Migrate to Gradle wrapper and sync project", CreateGradleWrapperQuickFix())
        }
      }
    }
    // Also offer quickFix to open Gradle settings. In case we can't find IDEA project, we can still offer this one.
    buildIssueComposer.addQuickFix("Gradle Settings.", OpenGradleSettingsQuickFix())

    return buildIssueComposer.composeBuildIssue()
  }

  private fun formatMessage(message: String?) : String? {
    if (message == null) return null
    val formattedMsg = StringBuilder()
    if (UNSUPPORTED_GRADLE_VERSION_PATTERN.matcher(message).matches()) {
      val index = message.indexOf("If using the gradle wrapper")
      if (index != -1) {
        formattedMsg.append(message.substring(0, index).trim())
      }
      else formattedMsg.append(message)
      if (formattedMsg.isNotEmpty() && !formattedMsg.endsWith('.')) formattedMsg.append('.')
      formattedMsg.append("\n\nPlease fix the project's Gradle settings.")
      return formattedMsg.toString()
    }
    return null
  }

  private fun getSupportedGradleVersion(message: String): String? {
    val matcher = UNSUPPORTED_GRADLE_VERSION_PATTERN.matcher(message)
    if (matcher.matches()) {
      val version = matcher.group(1)
      if (StringUtil.isNotEmpty(version)) {
        return version
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
    return UNSUPPORTED_GRADLE_VERSION_PATTERN.matcher(failureCause).matches()
  }

  class OpenGradleSettingsQuickFix: BuildIssueQuickFix {
    override val id = "open.gradle.settings"

    override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
      val future = CompletableFuture<Any>()
      invokeLater {
        val manager = ExternalSystemApiUtil.getManager(GradleProjectSystemUtil.GRADLE_SYSTEM_ID)
        assert(manager is GradleManager)
        val configurable = (manager as GradleManager).getConfigurable(project)
        ShowSettingsUtil.getInstance().editConfigurable(project, configurable)
        future.complete(null)
      }
      return future
    }
  }

  class FixGradleVersionInWrapperQuickFix(private var gradleWrapper: GradleWrapper?, gradleVersion: String?): DescribedBuildIssueQuickFix {
    override val description: String
      get() = "Change Gradle version in Gradle wrapper to $gradleVersion and re-import project"
    override val id = "fix.gradle.version.in.wrapper"
    val gradleVersion: String = gradleVersion ?: SdkConstants.GRADLE_LATEST_VERSION

    override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
      val future = CompletableFuture<Any>()
      invokeLater {
        if (gradleWrapper == null) gradleWrapper = GradleWrapper.find(project) ?: return@invokeLater
        gradleWrapper!!.updateDistributionUrlAndDisplayFailure(gradleVersion)
        // Set the distribution type and request sync.
        val settings = GradleProjectSettingsFinder.getInstance().findGradleProjectSettings(project)
        if (settings != null) {
          settings.distributionType = DistributionType.DEFAULT_WRAPPED
        }
        GradleSyncInvoker.getInstance().requestProjectSync(project, GradleSyncStats.Trigger.TRIGGER_QF_WRAPPER_GRADLE_VERSION_FIXED)
        future.complete(null)
      }
      return future
    }
  }
}