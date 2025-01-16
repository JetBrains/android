/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.idea.backup.asyncaction

import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.concurrency.coroutineScope
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** An action group that computes if it is enabled or disabled in a coroutine */
internal abstract class ActionGroupWithSuspendedUpdate : ActionGroup() {
  override fun update(e: AnActionEvent) {
    val project = e.project ?: return
    val job =
      project.coroutineScope.launch(AndroidDispatchers.workerThread) {
        withTimeout(500.milliseconds) {
          suspendedUpdate(project, e).applyTo(this@ActionGroupWithSuspendedUpdate, e.presentation)
        }
      }
    runBlocking { job.join() }
  }

  open fun AnActionEvent.getCoroutineScope(): CoroutineScope {
    return project?.coroutineScope ?: throw IllegalStateException("No CoroutineScope provided")
  }

  abstract suspend fun suspendedUpdate(project: Project, e: AnActionEvent): ActionEnableState
}
