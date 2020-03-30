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
import com.android.tools.idea.gradle.util.LocalProperties
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.invokeLater
import com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_QF_CMAKE_INSTALLED
import com.intellij.openapi.project.Project
import java.io.File
import java.util.concurrent.CompletableFuture

class SetCmakeDirQuickFix(private val myPath: File) : BuildIssueQuickFix {
  override val id = "SET_CMAKE_DIR"

  override fun runQuickFix(project: Project, dataProvider: DataProvider): CompletableFuture<*> {
    val future = CompletableFuture<Any>()

    invokeLater {
      val localProperties = LocalProperties(project)
      localProperties.androidCmakePath = myPath
      localProperties.save()
      GradleSyncInvoker.getInstance().requestProjectSync(project, TRIGGER_QF_CMAKE_INSTALLED)
      future.complete(null)
    }
    return future
  }
}