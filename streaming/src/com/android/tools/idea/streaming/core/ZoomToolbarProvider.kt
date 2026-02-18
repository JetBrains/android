/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.streaming.core

import com.android.tools.editor.EditorActionsFloatingToolbarProvider
import com.android.tools.editor.EditorActionsToolbarActionGroups
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import javax.swing.JComponent

internal class ZoomToolbarProvider private constructor(component: JComponent, parentDisposable: Disposable) :
  EditorActionsFloatingToolbarProvider(component, parentDisposable) {

  init {
    updateToolbar()
  }

  override fun getActionGroups() =
    object : EditorActionsToolbarActionGroups {
      override val zoomControlsGroup: ActionGroup
        get() {
          return DefaultActionGroup().apply {
            val actionManager = ActionManager.getInstance()
            add(actionManager.getAction("android.streaming.zoom.in"))
            add(actionManager.getAction("android.streaming.zoom.out"))
            add(actionManager.getAction("android.streaming.zoom.actual"))
            add(actionManager.getAction("android.streaming.zoom.fit"))
          }
        }
    }

  companion object {
    @JvmStatic
    fun createToolbar(component: JComponent, parentDisposable: Disposable): JComponent {
      return ZoomToolbarProvider(component, parentDisposable).floatingToolbar
    }
  }
}
