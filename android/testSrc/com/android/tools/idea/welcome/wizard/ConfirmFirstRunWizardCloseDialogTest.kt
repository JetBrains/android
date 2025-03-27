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
package com.android.tools.idea.welcome.wizard

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.testFramework.RunsInEdt
import java.util.function.BiFunction
import javax.swing.JCheckBox
import kotlin.test.assertEquals
import org.junit.Test
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.anyOrNull

@RunsInEdt
class ConfirmFirstRunWizardCloseDialogTest {

  @Test
  fun rerunWizardReturned_whenCheckboxSelectedAndOkClicked() {
    withMockedCheckboxDialogThatReturns(DialogWrapper.OK_EXIT_CODE, checkboxSelected = true) {
      val result = ConfirmFirstRunWizardCloseDialog.show()
      assertEquals(ConfirmFirstRunWizardCloseDialog.Result.Rerun, result)
    }
  }

  @Test
  fun skipWizardReturned_whenCheckboxNotSelectedAndOkClicked() {
    withMockedCheckboxDialogThatReturns(DialogWrapper.OK_EXIT_CODE, checkboxSelected = false) {
      val result = ConfirmFirstRunWizardCloseDialog.show()
      assertEquals(ConfirmFirstRunWizardCloseDialog.Result.Skip, result)
    }
  }

  @Test
  fun doNotCloseReturned_whenCancelClicked() {
    withMockedCheckboxDialogThatReturns(DialogWrapper.CANCEL_EXIT_CODE, checkboxSelected = false) {
      val result = ConfirmFirstRunWizardCloseDialog.show()
      assertEquals(ConfirmFirstRunWizardCloseDialog.Result.DoNotClose, result)
    }
    withMockedCheckboxDialogThatReturns(DialogWrapper.CANCEL_EXIT_CODE, checkboxSelected = true) {
      val result = ConfirmFirstRunWizardCloseDialog.show()
      assertEquals(ConfirmFirstRunWizardCloseDialog.Result.DoNotClose, result)
    }
  }

  private fun withMockedCheckboxDialogThatReturns(
    returnCode: Int,
    checkboxSelected: Boolean,
    func: () -> Unit,
  ) {
    Mockito.mockStatic(Messages::class.java).use { messages ->
      // Mock the dialog and capture the exitFunc
      messages
        .`when`<Any> {
          Messages.showCheckboxMessageDialog(
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
          )
        }
        .thenAnswer { invocation: InvocationOnMock ->
          // Simulate clicking OK and checkbox selected
          val exitFunc: BiFunction<Int, JCheckBox, Int> = invocation.getArgument(8)
          exitFunc.apply(
            returnCode,
            object : JCheckBox() {
              override fun isSelected(): Boolean {
                return checkboxSelected
              }
            },
          )
          returnCode
        }

      func()
    }
  }
}
