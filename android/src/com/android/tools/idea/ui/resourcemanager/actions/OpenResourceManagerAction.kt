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
package com.android.tools.idea.ui.resourcemanager.actions

import com.android.tools.idea.ui.resourcemanager.RESOURCE_EXPLORER_TOOL_WINDOW_ID
import com.intellij.facet.ProjectFacetManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.android.facet.AndroidFacet

/**
 * Opens the Resource Manager Tool Window
 */
internal class OpenResourceManagerAction : DumbAwareAction() {

  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabledAndVisible = project != null
                                         && ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID)
                                         && ToolWindowManager.getInstance(project).getToolWindow(RESOURCE_EXPLORER_TOOL_WINDOW_ID) != null
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT) ?: return
    ToolWindowManager.getInstance(project).getToolWindow(RESOURCE_EXPLORER_TOOL_WINDOW_ID)?.show(null)
  }
}