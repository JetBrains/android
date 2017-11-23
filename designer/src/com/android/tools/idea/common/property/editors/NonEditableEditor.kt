/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.common.property.editors

import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.uibuilder.property.editors.NlEditingListener
import com.intellij.ui.EditorTextField
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

class NonEditableEditor(listener: NlEditingListener) : BaseComponentEditor(listener) {
  val component = JPanel(BorderLayout())
  val textField = EditorTextField()
  lateinit var _property: NlProperty

  constructor() : this(NlEditingListener.DEFAULT_LISTENER)

  init {
    textField.border = EmptyBorder(VERTICAL_PADDING, HORIZONTAL_SPACING, VERTICAL_PADDING, HORIZONTAL_SPACING)
    component.add(textField, BorderLayout.CENTER)
    setEnabled(false)
    textField.isEnabled = false
  }

  override fun getComponent(): JComponent = component

  override fun getProperty() = _property

  override fun setProperty(property: NlProperty) {
    _property = property
    textField.text = value
  }

  override fun getValue(): String? {
    return _property.value
  }
}
