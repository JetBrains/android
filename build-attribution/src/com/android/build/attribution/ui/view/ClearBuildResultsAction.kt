/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.build.attribution.ui.view

import com.android.build.attribution.BuildAnalyzerStorageManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager

class ClearBuildResultsAction : AnAction("Clear Build Results") {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    if(project != null) {
      val runnable = Runnable {
        BuildAnalyzerStorageManager.getInstance(project).clearBuildResultsStored()
      }
      ProgressManager.getInstance()
        .runProcessWithProgressSynchronously(runnable, "Clear build analyzer results", false, project)
    }
  }
}