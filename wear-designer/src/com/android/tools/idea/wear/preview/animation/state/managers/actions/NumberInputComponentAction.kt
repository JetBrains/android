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
package com.android.tools.idea.wear.preview.animation.state.managers.actions

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JComponent

/**
 * An abstract base class for creating custom Swing components that allow user input of numeric
 * values (either Float or Int). It handles the creation of the input field, validation of user
 * input, and provides a callback mechanism to notify clients of valid value changes.
 *
 * @param T The numeric type accepted by this component (either Float or Int).
 * @param initialValue The initial value to display in the input field.
 * @param callback A function that will be called when the user enters a valid new value.
 */
abstract class NumberInputComponentAction<T : Number>(
  private val initialValue: T,
  val callback: (T) -> Unit,
) : AnAction(), CustomComponentAction {

  private var inputField: JBTextField? = null
  private var containerPanel: JBPanel<*>? = null

  override fun actionPerformed(e: AnActionEvent) {}

  override fun createCustomComponent(presentation: Presentation, actionPlace: String): JComponent {
    inputField =
      JBTextField(initialValue.toString(), 6).apply {
        addFocusListener(
          object : FocusAdapter() {
            override fun focusLost(e: FocusEvent?) {
              handleValueChange()
            }
          }
        )

        addKeyListener(
          object : KeyAdapter() {
            override fun keyPressed(keyEvent: KeyEvent) {
              if (keyEvent.keyCode == KeyEvent.VK_ENTER) {
                handleValueChange()
                inputField!!.transferFocus()
              }
            }
          }
        )
      }
    return JBPanel<Nothing>().apply { add(inputField) }.also { containerPanel = it }
  }

  private fun handleValueChange() {
    val editor =
      inputField?.let {
        CommonDataKeys.EDITOR.getData(DataManager.getInstance().getDataContext(it))
      }
    try {
      val newValue = parseInput(inputField!!.text.trim())
      callback(newValue)
    } catch (ex: NumberFormatException) {
      editor?.let {
        JBPopupFactory.getInstance()
          .createBalloonBuilder(JBLabel("Invalid input"))
          .createBalloon()
          .show(RelativePoint.getSouthOf(inputField!!), Balloon.Position.below)
      }
    } finally {
      editor?.contentComponent?.requestFocus()
    }
  }

  fun setInputFieldValue(newValue: String) {
    inputField?.text = newValue
  }

  // Abstract function to be implemented by derived classes
  protected abstract fun parseInput(input: String): T
}

class FloatInputComponentAction(initialValue: Float, function: (Float) -> Unit) :
  NumberInputComponentAction<Float>(initialValue, function) {
  override fun parseInput(input: String): Float = input.toFloat()
}

class IntInputComponentAction(initialValue: Int, function: (Int) -> Unit) :
  NumberInputComponentAction<Int>(initialValue, function) {
  override fun parseInput(input: String): Int = input.toInt()
}
