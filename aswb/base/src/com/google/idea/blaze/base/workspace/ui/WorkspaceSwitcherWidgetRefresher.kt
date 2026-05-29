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

import com.google.idea.blaze.base.workspace.activeWorkspaceStateFlow
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Project startup activity guaranteeing dynamic status bar widget creation and lifecycle re-evaluation. Subscribes to reactive mapping
 * streams and triggers platform widget manager updates to instantly install or dispose the UI element when availability changes.
 */
class WorkspaceSwitcherWidgetRefresher : ProjectActivity {
  override suspend fun execute(project: Project) {
    project.activeWorkspaceStateFlow.collect {
      withContext(Dispatchers.EDT) { project.service<StatusBarWidgetsManager>().updateWidget(WorkspaceSwitcherWidgetFactory::class.java) }
    }
  }
}
