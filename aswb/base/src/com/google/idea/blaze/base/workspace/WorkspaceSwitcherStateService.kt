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
package com.google.idea.blaze.base.workspace

import com.google.idea.blaze.base.workspace.WorkspaceSwitchManager.getWorkspacePath
import com.google.idea.blaze.base.workspace.WorkspaceSwitchManager.getWorkspaceTarget
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart

/**
 * Project-level reactive state publisher transforming global VFS mapping notifications into dedicated project-scoped physical workspace
 * state flows.
 */
@Service(Service.Level.PROJECT)
class WorkspaceSwitcherStateService(private val project: Project, val coroutineScope: CoroutineScope) {

  private val manualUpdates = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

  val activeWorkspaceStateFlow: Flow<String> =
    merge(WorkspaceSwitchManager.mappingChangeEvents, manualUpdates)
      .map { project.getWorkspacePathToPublish() }
      .onStart { emit(project.getWorkspacePathToPublish()) }
      .distinctUntilChanged()

  internal fun publishUpdate() {
    manualUpdates.tryEmit(Unit)
  }
}

/** Public extension property allowing any component across the IDE to instantly subscribe to reactive workspace transitions statelessly. */
val Project.activeWorkspaceStateFlow: Flow<String>
  get() = this.service<WorkspaceSwitcherStateService>().activeWorkspaceStateFlow

private fun Project.getWorkspacePathToPublish() = getWorkspaceTarget()?.toString() ?: getWorkspacePath().toString()
