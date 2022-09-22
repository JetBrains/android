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

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
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
import java.awt.event.KeyEvent

/** Tests the [TranslationsEditorTextField] class. */
@RunWith(JUnit4::class)
class TranslationsEditorTextFieldTest {
  private val projectRule = AndroidProjectRule.withAndroidModel()
  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  private val table: StringResourceTable = mock()
  private val model: StringResourceTableModel = mock()
  private var selectedColumn = 0

  private val translationsEditorTextField = TranslationsEditorTextField(table) { selectedColumn }

  private lateinit var fakeUi: FakeUi

  @Before
  fun setUp() {
    whenever(table.model).thenReturn(model)
    invokeAndWaitIfNeeded {
      fakeUi = FakeUi(translationsEditorTextField)
      fakeUi.root.validate()
    }
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
  fun doesNothingUntilKeyReleased() {
    whenever(table.hasSelectedCell()).thenReturn(true)
    fakeUi.keyboard.setFocus(translationsEditorTextField)
    fakeUi.keyboard.press(KeyEvent.VK_A)

    verifyNoInteractions(model)

    fakeUi.keyboard.release(KeyEvent.VK_A)

    verify(model).setValueAt(any(), any(), any())
  }

  @Test
  @RunsInEdt
  fun editsAppropriateColumn() {
    whenever(table.hasSelectedCell()).thenReturn(true)
    fakeUi.keyboard.setFocus(translationsEditorTextField)
    val testData = listOf(
      Triple("zweiundvierzig und sechzehn", 42, 16),
      Triple("soixante-dix-huit et soixante-quatre", 78, 64),
      Triple("trescientos veintinueve y quince", 329, 15),
    )

    testData.forEach { (text, i, j) ->
      translationsEditorTextField.text = text
      whenever(table.selectedModelRowIndex).thenReturn(i)
      selectedColumn = j
      fakeUi.keyboard.pressAndRelease(KeyEvent.VK_A)

      verify(model).setValueAt(text, i, j)
    }
  }
}
