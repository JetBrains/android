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
import com.android.tools.idea.Projects
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.errors.SyncErrorHandler.updateUsageTracker
import com.android.tools.idea.gradle.util.GradleProjectSettingsFinder
import com.android.tools.idea.gradle.util.GradleWrapper
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.settings.DistributionType
import java.io.IOException
import java.util.concurrent.CompletableFuture

class Gradle2RequiredIssueChecker : GradleIssueChecker {
  override fun check(issueData: GradleIssueData): BuildIssue? {
    val message = issueData.error.message ?: return null
    if (message.isEmpty() || !message.endsWith("org/codehaus/groovy/runtime/typehandling/ShortTypeHandling")) return null

    // Log metrics.
    invokeLater {
      updateUsageTracker(issueData.projectPath, GradleSyncFailure.GRADLE2_REQUIRED)
    }
    val createGradleWrapperQuickFix = CreateGradleWrapperQuickFix()
    return object : BuildIssue {
      override val title = "Gradle ${SdkConstants.GRADLE_MINIMUM_VERSION} is required."
      override val description = buildString {
        appendln("Gradle ${SdkConstants.GRADLE_MINIMUM_VERSION} is required.")
        append(message)
        appendln()
        append("<a href=\"${createGradleWrapperQuickFix.id}\">${createGradleWrapperQuickFix.linkText}</a>")
      }
      override val quickFixes = listOf(createGradleWrapperQuickFix)
      override fun getNavigatable(project: Project) = null
    }
  }

  companion object class CreateGradleWrapperQuickFix() : BuildIssueQuickFix {
    override val id = "MIGRATE_GRADLE_WRAPPER"
    val linkText = "Migrate to Gradle wrapper and sync project"

    override fun runQuickFix(project: Project, dataProvider: DataProvider): CompletableFuture<*> {
      val projectDirPath = Projects.getBaseDirPath(project)
      try {
        GradleWrapper.create(projectDirPath)
        val settings = GradleProjectSettingsFinder.getInstance().findGradleProjectSettings(project)
        if (settings != null) {
          settings.distributionType = DistributionType.DEFAULT_WRAPPED
        }

        GradleSyncInvoker.getInstance().requestProjectSync(project, GradleSyncStats.Trigger.TRIGGER_QF_WRAPPER_CREATED)
      }
      catch (e: IOException) {
        Messages.showErrorDialog(project, "Failed to create Gradle wrapper: " + e.message, "Quick Fix")
      }
      return CompletableFuture.completedFuture<Any>(null)
    }
  }


}