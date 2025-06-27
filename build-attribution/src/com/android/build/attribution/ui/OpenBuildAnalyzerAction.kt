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

package com.android.build.attribution.ui
import com.android.build.attribution.BuildAnalyzerStorageManager
import com.android.build.attribution.ui.analytics.BuildAttributionUiAnalytics
import com.android.tools.idea.util.CommonAndroidUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class OpenBuildAnalyzerAction : AnAction() {

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project == null || !CommonAndroidUtil.getInstance().isAndroidProject(project)) {
      e.presentation.isEnabledAndVisible = false
    }
    else {
      e.presentation.isEnabled = BuildAnalyzerStorageManager.getInstance(project).hasData()
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    BuildAttributionUiManager.getInstance(project)
      .openTab(BuildAttributionUiAnalytics.TabOpenEventSource.BUILD_MENU_ACTION)
  }
}