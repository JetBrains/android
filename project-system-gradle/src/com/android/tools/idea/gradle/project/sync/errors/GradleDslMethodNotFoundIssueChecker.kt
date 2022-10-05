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
import com.android.ide.common.repository.GradleVersion
import com.android.ide.common.repository.GradleVersion.AgpVersion
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider
import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.android.tools.idea.gradle.project.sync.idea.issues.updateUsageTracker
import com.android.tools.idea.gradle.project.sync.quickFixes.FixAndroidGradlePluginVersionQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenFileAtLocationQuickFix
import com.android.tools.idea.gradle.util.GradleProjectSettingsFinder
import com.android.tools.idea.gradle.util.GradleWrapper
import com.android.utils.isDefaultGradleBuildFile
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.commands.ActionCommand
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.plugins.gradle.codeInsight.actions.AddGradleDslPluginAction
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler
import org.jetbrains.plugins.gradle.settings.DistributionType
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import java.util.regex.Pattern

class GradleDslMethodNotFoundIssueChecker : GradleIssueChecker {
  private val GRADLE_DSL_METHOD_NOT_FOUND_ERROR_PREFIX = "Gradle DSL method not found"
  private val MISSING_METHOD_PATTERN = Pattern.compile("Could not find method (.*?) .*")

  override fun check(issueData: GradleIssueData): BuildIssue? {
    val message = GradleExecutionErrorHandler.getRootCauseAndLocation(issueData.error).first.message ?: return null
    if (!message.startsWith(GRADLE_DSL_METHOD_NOT_FOUND_ERROR_PREFIX)) return null

    // Log metrics.
    invokeLater {
      updateUsageTracker(issueData.projectPath, GradleSyncFailure.DSL_METHOD_NOT_FOUND)
    }

    val matcher = MISSING_METHOD_PATTERN.matcher(message)
    val buildIssueComposer =
      BuildIssueComposer("$GRADLE_DSL_METHOD_NOT_FOUND_ERROR_PREFIX: '${if (matcher.find()) matcher.group(1) else ""}'")

    // Create QuickFix to open the file where the error is located.
    when {
      issueData.filePosition != null && isDefaultGradleBuildFile(issueData.filePosition!!.file) ->
        updateNotificationWithBuildFile(issueData.filePosition!!, buildIssueComposer)
      issueData.filePosition != null -> buildIssueComposer.addQuickFix("Open file", OpenFileAtLocationQuickFix(issueData.filePosition!!))
    }

    return buildIssueComposer.composeBuildIssue()
  }

  private fun updateNotificationWithBuildFile(filePosition: FilePosition, buildIssueComposer: BuildIssueComposer) {
    buildIssueComposer.addDescription("\nPossible causes:")
    // TODO: Can I rely on project name from path ?.
    buildIssueComposer.addDescription("Your project may be using a version of the Android Gradle plug-in that does not contain the " +
                               "method (e.g. 'testCompile' was added in 1.1.0).")

    val pluginVersion = AgpVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get())
    buildIssueComposer.addQuickFix("Upgrade plugin to version ${pluginVersion} and sync project",
                            FixAndroidGradlePluginVersionQuickFix(pluginVersion, GradleVersion.parse(SdkConstants.GRADLE_LATEST_VERSION)))
    buildIssueComposer.addQuickFix("Open Gradle wrapper file", GetGradleSettingsQuickFix())
    buildIssueComposer.addQuickFix("Apply Gradle plugin", ApplyGradlePluginQuickFix(filePosition))
  }

  override fun consumeBuildOutputFailureMessage(message: String,
                                                failureCause: String,
                                                stacktrace: String?,
                                                location: FilePosition?,
                                                parentEventId: Any,
                                                messageConsumer: Consumer<in BuildEvent>): Boolean {
    return failureCause.startsWith(GRADLE_DSL_METHOD_NOT_FOUND_ERROR_PREFIX)
  }
}

class GetGradleSettingsQuickFix : BuildIssueQuickFix {
  override val id = "open.gradle.settings"

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val future = CompletableFuture<Any>()

    invokeLater {
      if (isUsingWrapper(project)) {
        val gradleWrapper = GradleWrapper.find(project) ?: return@invokeLater
        val propertiesFile = gradleWrapper.propertiesFile ?: return@invokeLater
        // Open properties file.
        val descriptor = OpenFileDescriptor(project, propertiesFile)
        FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
      }
      future.complete(null)
    }
    return future
  }

  private fun isUsingWrapper(project: Project) : Boolean {
    if (GradleWrapper.find(project) == null) return false
    // Check if we don't have a GradleWrapper, but the project uses a default wrapper configuration.
    val distributionType = GradleProjectSettingsFinder.getInstance().findGradleProjectSettings(project)?.distributionType
    return (distributionType == null || distributionType == DistributionType.DEFAULT_WRAPPED)
  }
}

class ApplyGradlePluginQuickFix(private val myFilePosition: FilePosition) : BuildIssueQuickFix {
  override val id = "apply.gradle.plugin"

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val future = CompletableFuture<Any>()

    invokeLater {
      val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(myFilePosition.file) ?: return@invokeLater
      OpenFileDescriptor(project, virtualFile, myFilePosition.startLine, myFilePosition.startColumn).navigate(true)

      val actionManager = ActionManager.getInstance()
      val action = actionManager.getAction(AddGradleDslPluginAction.ID)
      assert(action is AddGradleDslPluginAction)
      actionManager.tryToExecute(action, ActionCommand.getInputEvent(AddGradleDslPluginAction.ID), null,
                                 ActionPlaces.UNKNOWN, true)
      future.complete(null)
    }
    return future
  }
}