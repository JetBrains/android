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

import com.google.idea.blaze.base.projectview.ProjectViewManager
import com.google.idea.blaze.base.projectview.section.sections.EnableWorkspaceSwitcherSection
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import kotlin.jvm.optionals.getOrNull

class WorkspaceSwitcherWidgetFactory : StatusBarWidgetFactory {

  override fun getId(): String = "WorkspaceSwitcherWidget"

  override fun getDisplayName(): String = "Active Workspace"

  override fun isAvailable(project: Project): Boolean {
    val projectViewSet = ProjectViewManager.getInstance(project).projectViewSet ?: return false
    return projectViewSet.getScalarValue(EnableWorkspaceSwitcherSection.KEY).getOrNull() ?: false
  }

  override fun createWidget(project: Project): StatusBarWidget {
    return WorkspaceSwitcherStatusBarWidget(project)
  }

  override fun disposeWidget(widget: StatusBarWidget) {
    Disposer.dispose(widget)
  }

  override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}
