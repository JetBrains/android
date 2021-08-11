/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.ui

import com.android.tools.adtui.common.AdtSecondaryPanel
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.adtui.stdui.KeyStrokes
import com.android.tools.property.panel.api.PropertyEditorModel
import com.android.tools.idea.uibuilder.property.model.HorizontalEditorPanelModel
import java.awt.FlowLayout
import javax.swing.JComponent

/**
 * A panel for holding one or more editor components.
 *
 * Intercept left and right arrows and ask the model to move focus to the next editor.
 */
class HorizontalEditorPanel(private val model: HorizontalEditorPanelModel): AdtSecondaryPanel(FlowLayout(FlowLayout.LEADING, 2, 2)) {

  init {
    registerKeyboardAction({ model.prior() }, KeyStrokes.LEFT, WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
    registerKeyboardAction({ model.next() }, KeyStrokes.RIGHT, WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
    model.addListener(ValueChangedListener { updateFromModel() })
  }

  fun add(modelAndEditor: Pair<PropertyEditorModel, JComponent>) {
    model.add(modelAndEditor.first)
    add(modelAndEditor.second)
  }

  private fun updateFromModel() {
    isVisible = model.visible
  }
}
