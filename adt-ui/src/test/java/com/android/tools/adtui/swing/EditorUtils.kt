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
package com.android.tools.adtui.swing

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.impl.event.EditorEventMulticasterImpl

object EditorUtils {

  /**
   * Here we explicitly remove listeners added in `EditorMouseHoverPopupManager` to sidestep false
   * leakages as the lifecycles of those listeners are tied to the application which are not excluded
   * when checking leaks. (I filed https://youtrack.jetbrains.com/issue/IDEA-323699 -- hopefully it
   * could be resolved.)
   */
  fun cleanUpListenersFromEditorMouseHoverPopupManager() {
    val editorEventMulticaster =
      EditorFactory.getInstance().eventMulticaster as EditorEventMulticasterImpl

    editorEventMulticaster.listeners.onEach { (key, value) ->
      when (key) {
        CaretListener::class.java -> {
          val listener =
            value.firstOrNull {
              it.javaClass.name.startsWith(
                "com.intellij.openapi.editor.EditorMouseHoverPopupManager\$"
              )
            } as? CaretListener ?: return@onEach
          editorEventMulticaster.removeCaretListener(listener)
        }
        VisibleAreaListener::class.java -> {
          val listener =
            value.firstOrNull {
              it.javaClass.name.startsWith(
                "com.intellij.openapi.editor.EditorMouseHoverPopupManager\$"
              )
            } as? VisibleAreaListener ?: return@onEach
          editorEventMulticaster.removeVisibleAreaListener(listener)
        }
      }
    }
  }
}
