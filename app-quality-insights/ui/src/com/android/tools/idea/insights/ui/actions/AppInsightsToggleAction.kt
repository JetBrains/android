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
package com.android.tools.idea.insights.ui.actions

import com.intellij.ide.ActivityTracker
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.Icon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** A ToggleAction that gets its state from the provided [flow]. */
class AppInsightsToggleAction(
  text: String?,
  description: String?,
  icon: Icon?,
  private val flow: Flow<Boolean>,
  scope: CoroutineScope,
  private val onToggle: () -> Unit
) : ToggleAction(text, description, icon) {
  private val currentState = AtomicBoolean(true)

  init {
    scope.launch {
      flow.collect {
        if (currentState.compareAndSet(!it, it)) {
          ActivityTracker.getInstance().inc()
        }
      }
    }
  }

  override fun isSelected(e: AnActionEvent) = currentState.get()
  override fun setSelected(e: AnActionEvent, state: Boolean) {
    currentState.set(state)
    onToggle()
  }
}
