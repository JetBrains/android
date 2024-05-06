/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.actions

import com.android.tools.idea.gradle.project.Info
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.intellij.openapi.actionSystem.ActionPlaces.PROJECT_VIEW_POPUP
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project

private const val ACTION_TEXT = "Run generate sources Gradle tasks"
private const val MENU_TEXT = "Run Generate Sources Gradle Tasks"

class GenerateSourcesModuleAction : AndroidStudioGradleAction(ACTION_TEXT) {
  override fun doUpdate(e: AnActionEvent, project: Project) {
    val dataContext = e.dataContext

    val modules = Info.getInstance(project).getModulesToBuildFromSelection(dataContext)

    e.presentation.isEnabled = modules.isNotEmpty()
    e.presentation.text = MENU_TEXT
    e.presentation.isVisible = modules.isNotEmpty() || PROJECT_VIEW_POPUP != e.place
  }

  override fun doPerform(e: AnActionEvent, project: Project) {
    val modules = Info.getInstance(project).getModulesToBuildFromSelection(e.dataContext)
    GradleBuildInvoker.getInstance(project).generateSources(modules)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}