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
package com.android.tools.idea.preview.focus

import com.android.tools.idea.common.editor.ActionsToolbar
import com.android.tools.idea.preview.Colors
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import java.awt.BorderLayout
import javax.swing.JPanel
import kotlin.reflect.KProperty

/**
 * [FocusMode] property delegate to be used by views which need [FocusMode] support.
 *
 * When the [FocusMode] is not null, this property delegate replaces the given [mainSurface] from
 * the given [component] with a [JPanel] containing tabs provided by [FocusMode.component] at the
 * north and [mainSurface] in the center.
 *
 * When the [FocusMode] is null, the [mainSurface] is restored within [content] and the [JPanel]
 * with the focus tabs is removed.
 */
class FocusModeProperty(private val content: JPanel, private val mainSurface: NlDesignSurface) :
  JPanel(BorderLayout()) {

  private var focusMode: FocusMode? = null

  operator fun getValue(thisRef: Any?, property: KProperty<*>) = focusMode

  operator fun setValue(thisRef: Any?, property: KProperty<*>, value: FocusMode?) {
    // Avoid repeated values.
    if (value == focusMode) return
    // If essentials mode is enabled, disabled or updated - components should be rearranged.
    // Remove components from its existing places.
    if (focusMode == null) {
      content.remove(mainSurface)
    } else {
      removeAll()
      content.remove(this)
    }

    // Add components to new places.
    if (value == null) {
      content.add(mainSurface, BorderLayout.CENTER)
    } else {
      value.component.border = ActionsToolbar.BORDER
      value.component.background = Colors.DEFAULT_BACKGROUND_COLOR
      add(value.component, BorderLayout.NORTH)
      add(mainSurface, BorderLayout.CENTER)
      content.add(this)
    }
    focusMode = value
  }
}
