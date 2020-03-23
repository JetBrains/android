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
import com.android.tools.idea.gradle.plugin.AndroidPluginVersionUpdater
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.errors.SyncErrorHandler.updateUsageTracker
import com.android.tools.idea.gradle.project.sync.idea.issues.MessageComposer
import com.android.tools.idea.gradle.util.GradleProjectSettingsFinder
import com.android.tools.idea.gradle.util.GradleWrapper
import com.android.utils.isDefaultGradleBuildFile
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.build.FilePosition
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.commands.ActionCommand
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.pom.Navigatable
import org.jetbrains.plugins.gradle.codeInsight.actions.AddGradleDslPluginAction
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.settings.DistributionType
import java.util.concurrent.CompletableFuture
import java.util.regex.Pattern

class GradleDslMethodNotFoundIssueChecker : GradleIssueChecker {
  private val GRADLE_DSL_METHOD_NOT_FOUND_ERROR_PREFIX = "Gradle DSL method not found"
  private val MISSING_METHOD_PATTERN = Pattern.compile("Could not find method (.*?) .*")

  override fun check(issueData: GradleIssueData): BuildIssue? {
    val message = issueData.error.message ?: return null
    //val rootCause = issueData.error.cause ?: return null
    if (!message.startsWith(GRADLE_DSL_METHOD_NOT_FOUND_ERROR_PREFIX)) return null

    // Log metrics.
    invokeLater {
      updateUsageTracker(issueData.projectPath, GradleSyncFailure.DSL_METHOD_NOT_FOUND)
    }

    val matcher = MISSING_METHOD_PATTERN.matcher(message)
    val description = MessageComposer("$GRADLE_DSL_METHOD_NOT_FOUND_ERROR_PREFIX: '${if (matcher.find()) matcher.group(1) else ""}'")

    // Create QuickFix to open the file where the error is located.
    when {
      issueData.filePosition != null && isDefaultGradleBuildFile(issueData.filePosition!!.file) ->
        updateNotificationWithBuildFile(issueData.filePosition!!, description)
      issueData.filePosition != null -> description.addQuickFix("Open file", OpenFileAtLocationQuickFix(issueData.filePosition!!))
    }

    return object : BuildIssue {
      override val title = "Gradle Sync Issues."
      override val description = description.buildMessage()
      override val quickFixes = description.quickFixes
      override fun getNavigatable(project: Project) = null
    }
  }

  private fun updateNotificationWithBuildFile(filePosition: FilePosition, description: MessageComposer) {
    description.addDescription("\nPossible causes:")
    // TODO: Can I rely on project name from path ?.
    description.addDescription("Your project may be using a version of the Android Gradle plug-in that does not contain the " +
                               "method (e.g. 'testCompile' was added in 1.1.0).")

    val pluginVersion = GradleVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get())
    description.addQuickFix("Upgrade plugin to version ${pluginVersion} and sync project",
                            FixAndroidGradlePluginVersionQuickFix(pluginVersion, GradleVersion.parse(SdkConstants.GRADLE_LATEST_VERSION)))
    description.addQuickFix("Open Gradle wrapper file", GetGradleSettingsQuickFix())
    description.addQuickFix("Apply Gradle plugin", ApplyGradlePluginQuickFix(filePosition))
  }

  class OpenFileAtLocationQuickFix(val myFilePosition: FilePosition) : BuildIssueQuickFix {
    override val id = "OPEN_FILE"

    override fun runQuickFix(project: Project, dataProvider: DataProvider): CompletableFuture<*> {
      val projectFile = project.projectFile ?: return CompletableFuture.completedFuture<Any>(null)
      invokeLater {
        val file = projectFile.parent.fileSystem.findFileByPath(myFilePosition.file.path)
        if (file != null) {
          val openFile: Navigatable = OpenFileDescriptor(project, file, myFilePosition.startLine, myFilePosition.startColumn, false)
          if (openFile.canNavigate()) {
            openFile.navigate(true)
          }
        }
      }
      return CompletableFuture.completedFuture<Any>(null)
    }
  }

  /**
   * This QuickFix upgrades the Gradle model to the version in [SdkConstants.GRADLE_PLUGIN_RECOMMENDED_VERSION] and Gradle
   * to the version in [SdkConstants.GRADLE_LATEST_VERSION].
   */
  class FixAndroidGradlePluginVersionQuickFix(val pluginVersion: GradleVersion, val gradleVersion: GradleVersion) : BuildIssueQuickFix {
    override val id = "FIX_GRADLE_ELEMENTS"

    override fun runQuickFix(project: Project, dataProvider: DataProvider): CompletableFuture<*> {
      invokeLater {
        val updater = AndroidPluginVersionUpdater.getInstance(project)
        if (updater.updatePluginVersion(pluginVersion, gradleVersion)) {
          val request = GradleSyncInvoker.Request(GradleSyncStats.Trigger.TRIGGER_AGP_VERSION_UPDATED)
          GradleSyncInvoker.getInstance().requestProjectSync(project, request)
        }
      }
      return CompletableFuture.completedFuture<Any>(null)
    }
  }

  class GetGradleSettingsQuickFix : BuildIssueQuickFix {
    override val id = "OPEN_GRADLE_SETTINGS"

    override fun runQuickFix(project: Project, dataProvider: DataProvider): CompletableFuture<*> {
      invokeLater {
        if (isUsingWrapper(project)) {
          val gradleWrapper = GradleWrapper.find(project) ?: return@invokeLater
          val propertiesFile = gradleWrapper.propertiesFile ?: return@invokeLater
          // Open properties file.
          val descriptor = OpenFileDescriptor(project, propertiesFile)
          FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
        }
      }
      return CompletableFuture.completedFuture<Any>(null)
    }

    private fun isUsingWrapper(project: Project) : Boolean {
      if (GradleWrapper.find(project) == null) return false
      // Check if we don't have a GradleWrapper, but the project uses a default wrapper configuration.
      val distributionType = GradleProjectSettingsFinder.getInstance().findGradleProjectSettings(project)?.distributionType
      return (distributionType == null || distributionType == DistributionType.DEFAULT_WRAPPED)
    }
  }

  class ApplyGradlePluginQuickFix(private val myFilePosition: FilePosition) : BuildIssueQuickFix {
    override val id = "APPLY_GRADLE_PLUGIN"

    override fun runQuickFix(project: Project, dataProvider: DataProvider): CompletableFuture<*> {
      invokeLater {
        val virtualFile =
          LocalFileSystem.getInstance().findFileByIoFile(myFilePosition.file) ?: return@invokeLater
        OpenFileDescriptor(project, virtualFile, myFilePosition.startLine, myFilePosition.startColumn).navigate(true)

        val actionManager = ActionManager.getInstance()
        val action = actionManager.getAction(AddGradleDslPluginAction.ID)
        assert(action is AddGradleDslPluginAction)
        actionManager.tryToExecute(action, ActionCommand.getInputEvent(AddGradleDslPluginAction.ID), null,
                                   ActionPlaces.UNKNOWN, true)
      }
      return CompletableFuture.completedFuture<Any>(null)
    }
  }
}