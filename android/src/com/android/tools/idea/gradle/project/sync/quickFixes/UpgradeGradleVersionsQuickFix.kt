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
package com.android.tools.idea.gradle.project.sync.quickFixes

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.concurrency.AndroidExecutors
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.idea.issues.DescribedBuildIssueQuickFix
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeRefactoringProcessor
import com.android.tools.idea.gradle.project.upgrade.AndroidPluginVersionUpdater
import com.android.tools.idea.gradle.project.upgrade.showAndGetAgpUpgradeDialog
import com.android.tools.idea.gradle.util.GradleUtil
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import java.util.concurrent.CompletableFuture

class UpgradeGradleVersionsQuickFix(val gradleVersion: GradleVersion,
                                    val agpVersion: GradleVersion,
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
  override fun runQuickFix(project: Project, dataProvider: DataProvider): CompletableFuture<*> {
    val future = CompletableFuture<Any>()
    val runnable = Runnable {
      AndroidExecutors.getInstance().ioThreadExecutor.execute {
        var changesDone = false
        val currentAgpVersion = GradleUtil.getAndroidGradleModelVersionInUse(project)
        if ((currentAgpVersion == null) || (!StudioFlags.AGP_UPGRADE_ASSISTANT.get())) {
          val updater = AndroidPluginVersionUpdater.getInstance(project)
          if (updater.updatePluginVersion(agpVersion, gradleVersion)) {
            changesDone = true
            val request = GradleSyncInvoker.Request(GradleSyncStats.Trigger.TRIGGER_AGP_VERSION_UPDATED)
            GradleSyncInvoker.getInstance().requestProjectSync(project, request)
          }
        }
        else {
          val processor = AgpUpgradeRefactoringProcessor(project, currentAgpVersion, agpVersion)
          val runProcessor =
            if ((!isUnitTestMode()) || (showDialogResultForTest == null))
              showAndGetAgpUpgradeDialog(processor)
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
    if (isUnitTestMode()) {
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