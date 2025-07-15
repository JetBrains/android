/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.streaming.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/** An action that delegates to one of its delegate actions depending on context. */
internal abstract class DelegatingAction(vararg val delegates: AnAction) : AnAction() {

  override fun getActionUpdateThread(): ActionUpdateThread {
    for (delegate in delegates) {
      if (delegate.actionUpdateThread == ActionUpdateThread.EDT) {
        return ActionUpdateThread.EDT
      }
    }
    return ActionUpdateThread.BGT
  }

  override fun update(event: AnActionEvent) {
    getLeafDelegate(event).update(event)
  }

  override fun actionPerformed(event: AnActionEvent) {
    getLeafDelegate(event).actionPerformed(event)
  }

  /** Returns a delegate for the given [event] context that is not a [DelegatingAction]. */
  protected open fun getLeafDelegate(event: AnActionEvent): AnAction {
    var delegate = getDelegate(event)
    while (delegate is DelegatingAction) {
      delegate = delegate.getDelegate(event)
    }
    return delegate
  }

  /** Returns the delegate for the given [event] context. */
  abstract fun getDelegate(event: AnActionEvent): AnAction

  /** Checks if this action may delegate to the given [action]. */
  fun mayDelegate(action: AnAction): Boolean {
    for (delegate in delegates) {
      if (delegate == action || delegate is DelegatingAction && delegate.mayDelegate(action)) {
        return true
      }
    }
    return false
  }
}
