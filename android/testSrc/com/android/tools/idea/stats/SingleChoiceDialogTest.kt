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
package com.android.tools.idea.stats

import com.android.tools.adtui.swing.FakeUi
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Ref
import com.intellij.util.ui.UIUtil
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.verify
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JRadioButton
import javax.swing.SwingUtilities
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SingleChoiceDialogTest {

  lateinit var disposable: Disposable

  @Before
  fun setUp() {
    disposable = Disposer.newDisposable(this::class.simpleName!!)
  }

  @After
  fun tearDown() {
    SwingUtilities.invokeAndWait { Disposer.dispose(disposable) }
  }

  @Suppress("UnstableApiUsage")
  @Test
  fun testOK() {
    val result = Ref.create<SingleChoiceDialog>()
    val logger = Mockito.mock(ChoiceLogger::class.java)
    SwingUtilities.invokeAndWait {
      val dialog = createDialog(logger)
      result.set(dialog)
    }
    val dialog = result.get()
    val content = getContent(dialog)
    val radioButtons = UIUtil.findComponentsOfType(content, JRadioButton::class.java)
    assertFalse(dialog.isOKActionEnabled)
    for (button in radioButtons) {
      assertFalse(button.isSelected)
    }

    val fakeUi = FakeUi(content)
    clickButton(fakeUi, radioButtons[0])
    assertTrue(radioButtons[0].isSelected)
    assertTrue(dialog.isOKActionEnabled)

    SwingUtilities.invokeAndWait { dialog.performOKAction() }
    verify(logger).log(0)
  }

  @Test
  fun testCancel() {
    val result = Ref.create<SingleChoiceDialog>()
    val logger = Mockito.mock(ChoiceLogger::class.java)
    SwingUtilities.invokeAndWait {
      val dialog = createDialog(logger)
      result.set(dialog)
    }
    val dialog = result.get()
    val content = getContent(dialog)
    val radioButtons = UIUtil.findComponentsOfType(content, JRadioButton::class.java)
    assertFalse(dialog.isOKActionEnabled)
    for (button in radioButtons) {
      assertFalse(button.isSelected)
    }

    val fakeUi = FakeUi(content)
    clickButton(fakeUi, radioButtons[0])
    assertTrue(radioButtons[0].isSelected)
    assertTrue(dialog.isOKActionEnabled)

    SwingUtilities.invokeAndWait { dialog.doCancelAction(null) }
    verify(logger).cancel()
  }

  private fun getContent(singleChoiceDialog: SingleChoiceDialog): JComponent {
    val content = singleChoiceDialog.content
    content.isVisible = true
    content.size = Dimension(300, 400)
    content.preferredSize = Dimension(300, 400)
    return content
  }

  private fun createDialog(logger: ChoiceLogger): SingleChoiceDialog {
    val dialog = SingleChoiceDialog(DEFAULT_SATISFACTION_SURVEY, logger)
    Disposer.register(disposable, dialog.disposable)
    return dialog
  }

  private fun clickButton(fakeUi: FakeUi, button: JRadioButton) {
    val locationOnScreen = fakeUi.getPosition(button)
    fakeUi.mouse.press(locationOnScreen.x, locationOnScreen.y)
    fakeUi.mouse.release()
    fakeUi.mouse.click(locationOnScreen.x, locationOnScreen.y)
  }
}
