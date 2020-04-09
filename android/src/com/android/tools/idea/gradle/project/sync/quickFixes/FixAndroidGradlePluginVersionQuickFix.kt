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

import com.android.SdkConstants
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.plugin.AndroidPluginVersionUpdater
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import java.util.concurrent.CompletableFuture

/**
 * This QuickFix upgrades the Gradle model to the version in [SdkConstants.GRADLE_PLUGIN_RECOMMENDED_VERSION] and Gradle
 * to the version in [SdkConstants.GRADLE_LATEST_VERSION].
 */
class FixAndroidGradlePluginVersionQuickFix(givenPluginVersion: GradleVersion?, givenGradleVersion: GradleVersion?) : BuildIssueQuickFix {
  override val id = "fix.gradle.elements"
  val pluginVersion = givenPluginVersion ?: GradleVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get())
  val gradleVersion = givenGradleVersion ?: GradleVersion.parse(SdkConstants.GRADLE_LATEST_VERSION)

  override fun runQuickFix(project: Project, dataProvider: DataProvider): CompletableFuture<*> {
    val future = CompletableFuture<Any>()

    invokeLater {
      val updater = AndroidPluginVersionUpdater.getInstance(project)
      if (updater.updatePluginVersion(pluginVersion, gradleVersion)) {
        val request = GradleSyncInvoker.Request(GradleSyncStats.Trigger.TRIGGER_AGP_VERSION_UPDATED)
        GradleSyncInvoker.getInstance().requestProjectSync(project, request)
      }
      future.complete(null)
    }
    return future
  }
}