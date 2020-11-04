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
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.idea.issues.DescribedBuildIssueQuickFix
import com.android.tools.idea.gradle.project.upgrade.AndroidPluginVersionUpdater
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import java.util.concurrent.CompletableFuture

class UpgradeGradleVersionsQuickFix(val gradleVersion: GradleVersion,
                                    val agpVersion: GradleVersion,
                                    suffix: String): DescribedBuildIssueQuickFix {
  override val description: String = "Change to $suffix versions (plugin $agpVersion, Gradle $gradleVersion) and sync project"
  override val id: String = "upgrade.version.$suffix"
  private var showDialogResultForTest: Boolean? = null

  override fun runQuickFix(project: Project, dataProvider: DataProvider): CompletableFuture<*> {
    val future = CompletableFuture<Any>()

    val runnable = Runnable {
      val updater = AndroidPluginVersionUpdater.getInstance(project)
      if (updater.updatePluginVersion(agpVersion, gradleVersion)) {
        val request = GradleSyncInvoker.Request(GradleSyncStats.Trigger.TRIGGER_AGP_VERSION_UPDATED)
        GradleSyncInvoker.getInstance().requestProjectSync(project, request)
      }
      future.complete(null)
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
