/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.emulator

import com.android.tools.editor.EditorActionsFloatingToolbar
import com.android.tools.idea.uibuilder.editor.BasicDesignSurfaceActionGroups
import com.intellij.openapi.Disposable
import javax.swing.JComponent

class EmulatorZoomToolbar private constructor(
  private val component: JComponent,
  parentDisposable: Disposable
) : EditorActionsFloatingToolbar(component, parentDisposable) {

  init {
    updateToolbar()
  }

  override fun getActionGroups() = BasicDesignSurfaceActionGroups(component)

  companion object {
    @JvmStatic
    fun getToolbar(component: JComponent, parentDisposable: Disposable): JComponent {
      return EmulatorZoomToolbar(component, parentDisposable).designSurfaceToolbar
    }
  }
}
