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
package com.android.tools.idea.ui.resourcechooser.colorpicker2

import com.android.tools.adtui.stdui.CommonButton
import com.intellij.testFramework.UsefulTestCase.assertThrows
import junit.framework.TestCase
import org.mockito.Mockito
import java.awt.Color

class OperationPanelTest : TestCase() {

  fun testNoOperationWillThrowException() {
    assertThrows(IllegalStateException::class.java) {
      OperationPanel(ColorPickerModel(), null, null)
    }
  }

  fun testSetOkOperation() {
    val ok = Mockito.mock(Callback::class.java)

    val model = ColorPickerModel()
    val panel = OperationPanel(model, ok, null)

    val okButton = panel.getOkButton()
    val cancelButton = panel.getCancelButton()
    assertNotNull(okButton)
    assertNull(cancelButton)

    model.setColor(Color.YELLOW)
    okButton!!.doClick()
    Mockito.verify(ok).invoke(Color.YELLOW)
  }

  fun testSetCancelOperation() {
    val cancel = Mockito.mock(Callback::class.java)

    val model = ColorPickerModel()
    val panel = OperationPanel(model, null, cancel)

    val okButton = panel.getOkButton()
    val cancelButton = panel.getCancelButton()
    assertNull(okButton)
    assertNotNull(cancelButton)

    model.setColor(Color.RED)
    cancelButton!!.doClick()
    Mockito.verify(cancel).invoke(Color.RED)
  }

  fun testSetOkAndCancelOperation() {
    val ok = Mockito.mock(Callback::class.java)
    val cancel = Mockito.mock(Callback::class.java)

    val model = ColorPickerModel()
    val panel = OperationPanel(model, ok, cancel)

    val okButton = panel.getOkButton()
    val cancelButton = panel.getCancelButton()
    assertNotNull(okButton)
    assertNotNull(cancelButton)

    model.setColor(Color.BLUE)

    Mockito.verifyNoMoreInteractions(ok)
    Mockito.verifyNoMoreInteractions(cancel)

    okButton!!.doClick()
    Mockito.verify(ok).invoke(Color.BLUE)
    Mockito.verifyNoMoreInteractions(cancel)

    cancelButton!!.doClick()
    Mockito.verify(cancel).invoke(Color.BLUE)
  }

  private fun OperationPanel.getOkButton() = components
    .filterIsInstance<CommonButton>()
    .firstOrNull { it.text == "OK" }

  private fun OperationPanel.getCancelButton() = components
    .filterIsInstance<CommonButton>()
    .firstOrNull { it.text == "Cancel" }

  interface Callback : (Color) -> Unit
}
