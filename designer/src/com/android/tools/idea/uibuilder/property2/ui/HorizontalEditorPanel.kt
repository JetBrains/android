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
package com.android.tools.idea.uibuilder.property2.ui

import com.android.tools.adtui.common.AdtSecondaryPanel
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.idea.common.property2.api.PropertyEditorModel
import java.awt.FlowLayout
import com.android.tools.idea.uibuilder.property2.model.HorizontalEditorPanelModel
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.KeyStroke

/**
 * A panel for holding one or more editor components.
 *
 * Intercept left and right arrows and ask the model to move focus to the next editor.
 */
class HorizontalEditorPanel(private val model: HorizontalEditorPanelModel): AdtSecondaryPanel(FlowLayout(FlowLayout.LEADING, 2, 2)) {

  init {
    registerKeyboardAction({ model.prior() }, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
    registerKeyboardAction({ model.next() }, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
  }

  fun add(modelAndEditor: Pair<PropertyEditorModel, JComponent>) {
    model.add(modelAndEditor.first)
    add(modelAndEditor.second)
    model.addListener(ValueChangedListener { updateFromModel() })
  }

  private fun updateFromModel() {
    isVisible = model.visible
  }
}
