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

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.project.Project
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.util.Key
import java.util.concurrent.CompletableFuture

class SyncProjectRefreshingDependenciesQuickFix : BuildIssueQuickFix {
  override val id = "SYNC_PROJECT"
  val linkText = "Re-download dependencies and sync project (requires network)"
  private val EXTRA_GRADLE_COMMAND_LINE_OPTIONS_KEY = Key.create<Array<String>>("extra.gradle.command.line.options")

  override fun runQuickFix(project: Project, dataProvider: DataProvider): CompletableFuture<*> {
    project.putUserData(EXTRA_GRADLE_COMMAND_LINE_OPTIONS_KEY, arrayOf("--refresh-dependencies"))
    GradleSyncInvoker.getInstance().requestProjectSync(project, GradleSyncStats.Trigger.TRIGGER_QF_REFRESH_DEPENDENCIES)
    return CompletableFuture.completedFuture<Any>(null)
  }
}