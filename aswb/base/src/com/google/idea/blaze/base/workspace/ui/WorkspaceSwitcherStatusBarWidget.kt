/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.workspace.ui

import com.google.idea.blaze.base.workspace.WorkspaceDiscoverer
import com.google.idea.blaze.base.workspace.WorkspaceSwitchManager.getWorkspaceTarget
import com.google.idea.blaze.base.workspace.actions.SwitchActiveWorkspaceAction
import com.google.idea.blaze.base.workspace.activeWorkspaceStateFlow
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Consumer
import java.awt.event.MouseEvent
import javax.swing.Icon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class WorkspaceSwitcherStatusBarWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.MultipleTextValuesPresentation {

  private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
  private var statusBar: StatusBar? = null

  override fun ID(): String = "WorkspaceSwitcherWidget"

  override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

  override fun dispose() {
    scope.cancel()
    statusBar = null
  }

  override fun install(statusBar: StatusBar) {
    this.statusBar = statusBar
    scope.launch { project.activeWorkspaceStateFlow.collect { statusBar.updateWidget(ID()) } }
  }

  override fun getTooltipText(): String {
    val target = project.getWorkspaceTarget()
    return target?.toAbsolutePath()?.normalize()?.toString() ?: "No active workspace target mapped"
  }

  override fun getSelectedValue(): String {
    val target = project.getWorkspaceTarget() ?: return "Workspace"
    val discoverer = WorkspaceDiscoverer.getWorkspaceDiscoverer(project)
    val descriptor = discoverer.getWorkspaceDescriptor(target)
    return descriptor?.name ?: target.fileName?.toString() ?: "Workspace"
  }

  override fun getClickConsumer(): Consumer<MouseEvent>? = null

  override fun getPopupStep(): ListPopup? {
    val currentStatusBar = statusBar ?: return null
    val component = currentStatusBar.component ?: return null
    val relativePoint = RelativePoint.getCenterOf(component)
    return SwitchActiveWorkspaceAction.createSwitchPopup(project, relativePoint)
  }

  override fun getIcon(): Icon? = null
}
