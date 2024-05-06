/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.idea.IdeInfo
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.android.tools.idea.gradle.project.sync.idea.issues.DescribedBuildIssueQuickFix
import com.android.tools.idea.gradle.project.sync.idea.issues.fetchIdeaProjectForGradleProject
import com.android.tools.idea.gradle.project.sync.issues.SyncFailureUsageReporter
import com.android.tools.idea.gradle.project.sync.jdk.JdkUtils
import com.android.tools.idea.gradle.project.sync.quickFixes.SelectJdkFromFileSystemQuickFix
import com.android.tools.idea.gradle.project.sync.requestProjectSync
import com.android.tools.idea.sdk.IdeSdks
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.build.issue.BuildIssue
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import java.awt.EventQueue.invokeLater
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture

@Suppress("UnstableApiUsage")
class UnsupportedJdkMinimumVersionIssueChecker : GradleIssueChecker {
  override fun check(issueData: GradleIssueData): BuildIssue? {
    val message = when {
      issueData.error.message?.contains("Unsupported major.minor version 52.0") == true -> {
        SyncFailureUsageReporter.getInstance().collectFailure(issueData.projectPath, AndroidStudioEvent.GradleSyncFailure.JDK8_REQUIRED)
        "${issueData.error.message!!}\nPlease use JDK 8 or newer."
      }
      else -> return null
    }

    return BuildIssueComposer(message).apply {
      if (IdeInfo.getInstance().isAndroidStudio) {
        fetchIdeaProjectForGradleProject(issueData.projectPath)?.let {
          if (!IdeSdks.getInstance().isUsingJavaHomeJdk(it)) {
            addUseJavaHomeQuickFix(this)
          }
        }

        if (issueQuickFixes.isEmpty()) {
          addQuickFix(UseEmbeddedJdkQuickFix())
        }
      }

      addQuickFix(SelectJdkFromFileSystemQuickFix())
    }.composeBuildIssue()
  }

  private fun addUseJavaHomeQuickFix(composer: BuildIssueComposer) {
    val ideSdks = IdeSdks.getInstance()
    val jdkFromHome = ideSdks.jdkFromJavaHome
    if (jdkFromHome != null && ideSdks.validateJdkPath(Paths.get(jdkFromHome)) != null) {
      composer.addQuickFix(UseJavaHomeAsJdkQuickFix())
    }
  }

  private class UseJavaHomeAsJdkQuickFix : DescribedBuildIssueQuickFix {
    override val description: String = "Set Android Studio to use the same JDK as Gradle and sync project"
    override val id: String = "use.java.home.as.jdk"

    override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
      val future = CompletableFuture<Nothing>()
      invokeLater {
        runWriteAction { JdkUtils.setProjectGradleJvmToUseJavaHome(project, project.basePath.orEmpty()) }
        GradleSyncInvoker.getInstance().requestProjectSync(project, GradleSyncStats.Trigger.TRIGGER_QF_JDK_CHANGED_TO_CURRENT)
        future.complete(null)
      }
      return future
    }
  }

  private class UseEmbeddedJdkQuickFix : DescribedBuildIssueQuickFix {
    override val description: String = "Use embedded JDK as Gradle JDK (${IdeSdks.getInstance().embeddedJdkPath})"
    override val id: String = "use.embedded.jdk"

    override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
      val future = CompletableFuture<Nothing>()
      invokeLater {
        runWriteAction { JdkUtils.setProjectGradleJvmToUseEmbeddedJdk(project, project.basePath.orEmpty()) }
        GradleSyncInvoker.getInstance().requestProjectSync(project, GradleSyncStats.Trigger.TRIGGER_QF_JDK_CHANGED_TO_EMBEDDED)
        future.complete(null)
      }
      return future
    }
  }
}
