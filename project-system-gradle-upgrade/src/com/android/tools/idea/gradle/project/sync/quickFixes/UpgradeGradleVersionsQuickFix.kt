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
package com.android.tools.idea.gradle.project.sync.quickFixes

import com.android.ide.common.repository.GradleVersion
import com.android.ide.common.repository.GradleVersion.AgpVersion
import com.android.tools.idea.concurrency.AndroidExecutors
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.idea.issues.DescribedBuildIssueQuickFix
import com.android.tools.idea.gradle.project.upgrade.AndroidPluginVersionUpdater
import com.android.tools.idea.gradle.project.upgrade.RefactoringProcessorInstantiator
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import java.util.concurrent.CompletableFuture

class UpgradeGradleVersionsQuickFix(val gradleVersion: GradleVersion,
                                    val agpVersion: AgpVersion,
                                    suffix: String): DescribedBuildIssueQuickFix {
  override val description: String = "Change to $suffix versions (plugin $agpVersion, Gradle $gradleVersion) and sync project"
  override val id: String = "upgrade.version.$suffix"
  private var showDialogResultForTest: Boolean? = null

  @VisibleForTesting
  fun showDialogResult(result: Boolean?) {
    showDialogResultForTest = result
  }

  /**
   * Try to use AgpUpgradeRefactoringProcessor to upgrade [project] to [gradleVersion] and [agpVersion]. If not possible to use, then use
   * AndroidPluginVersionUpdater.
   *
   * @return A [CompletableFuture] that will be a [Boolean], indicating whether the changes were applied or not.
   */
  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val future = CompletableFuture<Any>()
    val runnable = Runnable {
      AndroidExecutors.getInstance().diskIoThreadExecutor.execute {
        var changesDone = false
        val currentAgpVersion =
          GradleProjectSystemUtil.getAndroidGradleModelVersionInUse(project)
        if (currentAgpVersion == null) {
          val updater = AndroidPluginVersionUpdater.getInstance(project)
          if (updater.updatePluginVersion(agpVersion, gradleVersion)) {
            changesDone = true
            val request = GradleSyncInvoker.Request(GradleSyncStats.Trigger.TRIGGER_AGP_VERSION_UPDATED)
            GradleSyncInvoker.getInstance().requestProjectSync(project, request)
          }
        }
        else {
          val refactoringProcessorInstantiator = project.getService(RefactoringProcessorInstantiator::class.java)
          val processor = refactoringProcessorInstantiator.createProcessor(project, currentAgpVersion, agpVersion)
          val runProcessor =
            if ((!ApplicationManager.getApplication().isUnitTestMode) || (showDialogResultForTest == null))
              refactoringProcessorInstantiator.showAndGetAgpUpgradeDialog(processor)
            else
              showDialogResultForTest!!
          if (runProcessor) {
            DumbService.getInstance(project).smartInvokeLater {
              processor.run()
            }
            changesDone = true
          }
        }
        future.complete(changesDone)
      }
    }
    if (ApplicationManager.getApplication().isUnitTestMode) {
      runnable.run()
    }
    else {
      invokeLater {
        runnable.run()
      }
    }
    return future
  }
}