/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.property.panel.impl.model.util

import com.intellij.ide.ui.laf.darcula.ui.DarculaComboBoxUI
import javax.swing.JComboBox
import javax.swing.JComponent

class FakeComboBoxUI : DarculaComboBoxUI() {
  override fun installUI(component: JComponent) {
    @Suppress("UNCHECKED_CAST")
    comboBox = component as JComboBox<Any>
    popup = FakeComboPopup(comboBox)
    installListeners()
    installComponents()
    installKeyboardActions()
  }

  override fun setPopupVisible(comboBox: JComboBox<*>?, visible: Boolean) {
    if (visible) {
      popup.show()
    }
    else {
      popup.hide()
    }
  }

  override fun isPopupVisible(comboBox: JComboBox<*>?): Boolean = popup.isVisible
}