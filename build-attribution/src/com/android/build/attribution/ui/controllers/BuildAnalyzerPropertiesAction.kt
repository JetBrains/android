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
package com.android.build.attribution.ui.controllers

import com.android.build.attribution.BuildAnalyzerConfigurableProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil

/** Action to open Build Analyzer properties from BA UI. */
class BuildAnalyzerPropertiesAction: AnAction(
  { "${BuildAnalyzerConfigurableProvider.DISPLAY_NAME} Properties" },
  AllIcons.General.Settings
) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    if (project != null) {
      ShowSettingsUtil.getInstance().showSettingsDialog(project, BuildAnalyzerConfigurableProvider.DISPLAY_NAME)
    }
  }

}