/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.adtui.categorytable

import java.awt.Color
import javax.swing.JButton
import javax.swing.JComponent

/** Information about table-specific properties needed to render a component. */
data class TablePresentation(
  val foreground: Color,
  val background: Color,
  val rowSelected: Boolean,
)

/** A component that can directly accept TablePresentation changes. */
interface TableComponent {
  fun updateTablePresentation(manager: TablePresentationManager, presentation: TablePresentation)
}

/**
 * Class responsible for applying TablePresentation to a component. By default, walks the component
 * tree setting foreground and background recursively. If it finds a [TableComponent], delegates
 * updating to it.
 */
class TablePresentationManager {
  fun applyPresentation(c: JComponent, presentation: TablePresentation) {
    when (c) {
      is TableComponent -> c.updateTablePresentation(this, presentation)
      else -> defaultApplyPresentation(c, presentation)
    }
  }

  fun defaultApplyPresentation(c: JComponent, presentation: TablePresentation) {
    when (c) {
      // JButtons have their own background
      is JButton -> {}
      else -> presentation.applyColors(c)
    }
    applyPresentationToChildren(c, presentation)
  }

  fun applyPresentationToChildren(c: JComponent, presentation: TablePresentation) {
    c.componentList.filterIsInstance<JComponent>().forEach { applyPresentation(it, presentation) }
  }
}

fun TablePresentation.applyColors(c: JComponent) {
  c.foreground = foreground
  c.background = background
}
