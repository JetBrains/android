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

class SatisfactionDialogTest {

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
    val result = Ref.create<SatisfactionDialog>()
    val surveyLogger = Mockito.mock(SurveyLogger::class.java)
    SwingUtilities.invokeAndWait {
      val satisfactionDialog = createSatisfactionDialog(surveyLogger)
      result.set(satisfactionDialog)
    }
    val satisfactionDialog = result.get()
    val content = getContent(satisfactionDialog)
    val radioButtons = UIUtil.findComponentsOfType(content, JRadioButton::class.java)
    assertFalse(satisfactionDialog.isOKActionEnabled)
    for (button in radioButtons) {
      assertFalse(button.isSelected)
    }

    val fakeUi = FakeUi(content)
    clickButton(fakeUi, radioButtons[0])
    assertTrue(radioButtons[0].isSelected)
    assertTrue(satisfactionDialog.isOKActionEnabled)

    SwingUtilities.invokeAndWait { satisfactionDialog.performOKAction() }
    verify(surveyLogger).log(0)
  }

  @Test
  fun testCancel() {
    val result = Ref.create<SatisfactionDialog>()
    val surveyLogger = Mockito.mock(SurveyLogger::class.java)
    SwingUtilities.invokeAndWait {
      val satisfactionDialog = createSatisfactionDialog(surveyLogger)
      result.set(satisfactionDialog)
    }
    val satisfactionDialog = result.get()
    val content = getContent(satisfactionDialog)
    val radioButtons = UIUtil.findComponentsOfType(content, JRadioButton::class.java)
    assertFalse(satisfactionDialog.isOKActionEnabled)
    for (button in radioButtons) {
      assertFalse(button.isSelected)
    }

    val fakeUi = FakeUi(content)
    clickButton(fakeUi, radioButtons[0])
    assertTrue(radioButtons[0].isSelected)
    assertTrue(satisfactionDialog.isOKActionEnabled)

    SwingUtilities.invokeAndWait { satisfactionDialog.doCancelAction(null) }
    verify(surveyLogger).cancel()
  }

  private fun getContent(satisfactionDialog: SatisfactionDialog): JComponent {
    val content = satisfactionDialog.content
    content.isVisible = true
    content.size = Dimension(300, 400)
    content.preferredSize = Dimension(300, 400)
    return content
  }

  private fun createSatisfactionDialog(logger: SurveyLogger): SatisfactionDialog {
    val satisfactionDialog = SatisfactionDialog(DEFAULT_SATISFACTION_SURVEY, logger)
    Disposer.register(disposable, satisfactionDialog.disposable)
    return satisfactionDialog
  }

  private fun clickButton(fakeUi: FakeUi, button: JRadioButton) {
    val locationOnScreen = fakeUi.getPosition(button)
    fakeUi.mouse.press(locationOnScreen.x, locationOnScreen.y)
    fakeUi.mouse.release()
    fakeUi.mouse.click(locationOnScreen.x, locationOnScreen.y)
  }
}
