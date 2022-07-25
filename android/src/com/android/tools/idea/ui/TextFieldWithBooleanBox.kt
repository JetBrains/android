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
package com.android.tools.idea.ui

import com.android.tools.adtui.TextFieldWithLeftComponent
import com.intellij.ui.DocumentAdapter
import com.intellij.util.ui.ThreeStateCheckBox
import java.util.Locale
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.event.DocumentEvent

/**
 * Returns the given [JTextField] wrapped in a [JPanel] with a boolean checkbox.
 *
 * Clicking the checkbox will toggle through boolean values in the given [JTextField]. Similarly, typing boolean values in the textfield
 * will update the appearance of the boolean checkbox.
 *
 * @see ThreeStateCheckBox
 * @see TextFieldWithLeftComponent
 */
fun JTextField.wrapWithBooleanCheckBox(defaultValue: Boolean): JPanel {
  val booleanCheckBox = ThreeStateCheckBox()
  booleanCheckBox.addItemListener {
    text = booleanCheckBox.state.stateToBoolean()
  }
  booleanCheckBox.state = if (defaultValue) ThreeStateCheckBox.State.SELECTED else ThreeStateCheckBox.State.NOT_SELECTED
  val textFieldWithIcon = TextFieldWithLeftComponent(booleanCheckBox, this)

  this.document.addDocumentListener(object : DocumentAdapter() {
    override fun textChanged(e: DocumentEvent) {
      booleanCheckBox.state = this@wrapWithBooleanCheckBox.text.trim().toState()
    }
  })
  return textFieldWithIcon
}

private fun ThreeStateCheckBox.State.stateToBoolean(): String {
  return when (this) {
    ThreeStateCheckBox.State.SELECTED -> "true"
    ThreeStateCheckBox.State.NOT_SELECTED -> "false"
    ThreeStateCheckBox.State.DONT_CARE -> ""
  }
}

private fun String.toState(): ThreeStateCheckBox.State {
  return when (this.lowercase(Locale.US)) {
    "true" -> ThreeStateCheckBox.State.SELECTED
    "false" -> ThreeStateCheckBox.State.NOT_SELECTED
    else -> ThreeStateCheckBox.State.DONT_CARE
  }
}