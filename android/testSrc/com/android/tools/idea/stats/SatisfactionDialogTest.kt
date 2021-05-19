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
import com.google.wireless.android.sdk.stats.UserSentiment
import com.intellij.ide.ui.laf.darcula.DarculaLaf
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Ref
import com.intellij.util.ui.UIUtil
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JRadioButton
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.plaf.metal.MetalLookAndFeel
import kotlin.test.assertEquals
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

  @Test
  fun selection() {
    val result = Ref.create<SatisfactionDialog>()
    SwingUtilities.invokeAndWait {
      val satisfactionDialog = createSatisfactionDialog()
      result.set(satisfactionDialog)
    }
    val satisfactionDialog = result.get()
    val content = getContent(satisfactionDialog)
    val radioButtons = UIUtil.findComponentsOfType(content, JRadioButton::class.java)
    val fakeUi = FakeUi(content)
    clickButton(fakeUi, radioButtons[0])
    assertEquals(UserSentiment.SatisfactionLevel.VERY_SATISFIED, satisfactionDialog.selectedSentiment)
    clickButton(fakeUi, radioButtons[1])
    assertEquals(UserSentiment.SatisfactionLevel.SATISFIED, satisfactionDialog.selectedSentiment)
    clickButton(fakeUi, radioButtons[2])
    assertEquals(UserSentiment.SatisfactionLevel.NEUTRAL, satisfactionDialog.selectedSentiment)
    clickButton(fakeUi, radioButtons[3])
    assertEquals(UserSentiment.SatisfactionLevel.DISSATISFIED, satisfactionDialog.selectedSentiment)
    clickButton(fakeUi, radioButtons[4])
    assertEquals(UserSentiment.SatisfactionLevel.VERY_DISSATISFIED, satisfactionDialog.selectedSentiment)
  }

  private fun getContent(satisfactionDialog: SatisfactionDialog): JComponent {
    val content = satisfactionDialog.content
    content.isVisible = true
    content.size = Dimension(300, 400)
    content.preferredSize = Dimension(300, 400)
    return content
  }

  @Test
  fun selectionAndEnter() {
    val result = Ref.create<SatisfactionDialog>()
    SwingUtilities.invokeAndWait {
      val satisfactionDialog = createSatisfactionDialog()
      result.set(satisfactionDialog)
    }
    val satisfactionDialog = result.get()
    val content = getContent(satisfactionDialog)
    val radioButtons = UIUtil.findComponentsOfType(content, JRadioButton::class.java)
    assertFalse(satisfactionDialog.isOKActionEnabled)
    val fakeUi = FakeUi(content)
    clickButton(fakeUi, radioButtons[0])
    assertTrue(satisfactionDialog.isOKActionEnabled)
    assertEquals(UserSentiment.SatisfactionLevel.VERY_SATISFIED, satisfactionDialog.selectedSentiment)
  }

  @Test
  fun cancel() {
    val result = Ref.create<SatisfactionDialog>()
    SwingUtilities.invokeAndWait {
      val satisfactionDialog = createSatisfactionDialog()
      result.set(satisfactionDialog)
    }
    val satisfactionDialog = result.get()
    SwingUtilities.invokeAndWait { satisfactionDialog.doCancelAction() }
    assertEquals(UserSentiment.SatisfactionLevel.UNKNOWN_SATISFACTION_LEVEL, satisfactionDialog.selectedSentiment)
  }

  @Test
  fun selectionAndCancel() {
    val result = Ref.create<SatisfactionDialog>()
    SwingUtilities.invokeAndWait {
      val satisfactionDialog = createSatisfactionDialog()
      result.set(satisfactionDialog)
    }
    val satisfactionDialog = result.get()
    val content = getContent(satisfactionDialog)
    val radioButtons = UIUtil.findComponentsOfType(content, JRadioButton::class.java)
    val fakeUi = FakeUi(content)
    clickButton(fakeUi, radioButtons[0])
    assertEquals(UserSentiment.SatisfactionLevel.VERY_SATISFIED, satisfactionDialog.selectedSentiment)
    SwingUtilities.invokeAndWait { satisfactionDialog.doCancelAction() }
    assertEquals(UserSentiment.SatisfactionLevel.UNKNOWN_SATISFACTION_LEVEL, satisfactionDialog.selectedSentiment)
  }

  private fun createSatisfactionDialog(): SatisfactionDialog {
    val satisfactionDialog = SatisfactionDialog()
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

private fun main(vararg args: String) {
  UIManager.setLookAndFeel(MetalLookAndFeel())
  UIManager.setLookAndFeel(DarculaLaf())
  SwingUtilities.invokeAndWait {
    val satisfactionDialog = SatisfactionDialog()
    satisfactionDialog.showAndGetOk().doWhenDone(Runnable {
      println(satisfactionDialog.selectedSentiment)
    })
  }
}