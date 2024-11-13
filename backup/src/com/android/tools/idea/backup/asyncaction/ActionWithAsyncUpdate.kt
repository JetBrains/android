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

import com.android.tools.idea.concurrency.coroutineScope
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.VisibleForTesting

/** An action that computes if it is enabled or disabled in a coroutine */
internal abstract class ActionWithAsyncUpdate : AnAction() {
  @VisibleForTesting val updateState = AtomicReference<ActionEnableState?>(null)

  override fun update(e: AnActionEvent) {
    updateState.get()?.applyTo(this, e.presentation)

    val project = e.project ?: return
    e.getCoroutineScope().launch {
      val newState = computeState(project, e)
      val oldState = updateState.getAndSet(newState)
      if (oldState != newState) {
        ActivityTracker.getInstance().inc()
      }
    }
  }

  open fun AnActionEvent.getCoroutineScope(): CoroutineScope {
    return project?.coroutineScope ?: throw IllegalStateException("No CoroutineScope provided")
  }

  abstract suspend fun computeState(project: Project, e: AnActionEvent): ActionEnableState
}
