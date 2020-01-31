/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.ui

import com.android.tools.adtui.common.AdtSecondaryPanel
import com.android.tools.idea.layoutinspector.model.SelectedViewModel
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout

/**
 * For displaying which component is being edited.
 *
 * Intended for being shown at the top of the properties panel.
 * Display the component icon (from the palette) the id and a
 * description label on the right.
 */
class SelectedViewPanel(model: SelectedViewModel) : AdtSecondaryPanel(BorderLayout()) {
  private val component: JBLabel = JBLabel()
  private val description = JBLabel()

  init {
    border = JBUI.Borders.empty(0, 6, 0, 6)
    add(component, BorderLayout.WEST)
    add(description, BorderLayout.EAST)
    component.icon = model.icon
    component.text = model.id
    description.foreground = JBColor(Gray._192, Gray._128)
    description.text = model.description
  }
}
