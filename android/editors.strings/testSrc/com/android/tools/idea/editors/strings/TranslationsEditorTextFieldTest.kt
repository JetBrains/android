/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.editors.strings

import com.android.tools.adtui.swing.FakeKeyboardFocusManager
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.editors.strings.table.StringResourceTable
import com.android.tools.idea.editors.strings.table.StringResourceTableModel
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.awt.event.FocusEvent
import java.awt.event.KeyEvent

/** Tests the [TranslationsEditorTextField] class. */
@RunWith(JUnit4::class)
class TranslationsEditorTextFieldTest {
  private val projectRule = AndroidProjectRule.withAndroidModel()
  @get:Rule val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  private val table: StringResourceTable = mock()
  private val model: StringResourceTableModel = mock()
  private var selectedColumn = 0

  private val translationsEditorTextField = TranslationsEditorTextField(table) { selectedColumn }

  private lateinit var fakeUi: FakeUi
  private lateinit var focusManager: FakeKeyboardFocusManager

  @Before
  fun setUp() {
    whenever(table.model).thenReturn(model)
    invokeAndWaitIfNeeded {
      fakeUi = FakeUi(translationsEditorTextField, createFakeWindow = true)
      fakeUi.root.validate()
    }
    focusManager = FakeKeyboardFocusManager(projectRule.testRootDisposable)
  }

  @Test
  @RunsInEdt
  fun doesNothingIfNoSelectedCell() {
    fakeUi.keyboard.setFocus(translationsEditorTextField)
    fakeUi.keyboard.pressAndRelease(KeyEvent.VK_A)

    verifyNoInteractions(model)
  }

  @Test
  @RunsInEdt
  fun editsAppropriateColumn() {
    whenever(table.hasSelectedCell()).thenReturn(true)

    // Simulate weird focus behaviour in JTextComponent:
    fakeUi.keyboard.setFocus(translationsEditorTextField)
    translationsEditorTextField.text = "Original Value"
    translationsEditorTextField.focusListeners.forEach { it.focusGained(FocusEvent(translationsEditorTextField, FocusEvent.FOCUS_GAINED)) }

    val testData =
      listOf(
        Triple("zweiundvierzig und sechzehn", 42, 16),
        Triple("soixante-dix-huit et soixante-quatre", 78, 64),
        Triple("trescientos veintinueve y quince", 329, 15),
      )

    testData.forEach { (text, i, j) ->
      imitateEditing(text)
      whenever(table.selectedModelRowIndex).thenReturn(i)
      selectedColumn = j
      fakeUi.keyboard.pressAndRelease(KeyEvent.VK_ENTER)

      verify(model).setValueAt(text, i, j)
    }
  }

  @Test
  fun valueSavedWhenFocusLost() {
    whenever(table.hasSelectedCell()).thenReturn(true)

    // Simulate weird focus behaviour in JTextComponent:
    fakeUi.keyboard.setFocus(translationsEditorTextField)
    translationsEditorTextField.text = "Original Value"
    whenever(table.selectedModelRowIndex).thenReturn(13)
    selectedColumn = 17

    translationsEditorTextField.focusListeners.forEach { it.focusGained(FocusEvent(translationsEditorTextField, FocusEvent.FOCUS_GAINED)) }
    imitateEditing("Hello")
    translationsEditorTextField.focusListeners.forEach { it.focusLost(FocusEvent(translationsEditorTextField, FocusEvent.FOCUS_LOST)) }
    verify(model).setValueAt("Hello", 13, 17)
  }

  @Test
  fun valueNotChangedIfNothingChangedWhenFocusLost() {
    whenever(table.hasSelectedCell()).thenReturn(true)

    // Simulate weird focus behaviour in JTextComponent:
    fakeUi.keyboard.setFocus(translationsEditorTextField)
    translationsEditorTextField.text = "Original Value"
    translationsEditorTextField.focusListeners.forEach { it.focusGained(FocusEvent(translationsEditorTextField, FocusEvent.FOCUS_GAINED)) }
    translationsEditorTextField.focusListeners.forEach { it.focusLost(FocusEvent(translationsEditorTextField, FocusEvent.FOCUS_LOST)) }
    verifyNoInteractions(model)
  }

  private fun imitateEditing(newText: String) {
    val document = translationsEditorTextField.document
    document.remove(0, document.length)
    document.insertString(0, newText, null)
  }
}
