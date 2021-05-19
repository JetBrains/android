/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.adb.wireless

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.ui.SimpleDialog
import com.android.tools.idea.ui.SimpleDialogOptions
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.util.ui.JBDimension
import javax.swing.JComponent

@UiThread
class PinCodePairingDialog(project: Project) {
  private val dialog: SimpleDialog
  private val pairingPanel by lazy { PinCodeInputPanel() }

  init {
    val options = SimpleDialogOptions(project,
                                      true,
                                      DialogWrapper.IdeModalityType.PROJECT,
                                      title = "Enter PIN code",
                                      isModal = true,
                                      okButtonText = "Pair",
                                      centerPanelProvider = { createCenterPanel() },
                                      okActionHandler = { okButtonHandler() },
                                      preferredFocusProvider = { pairingPanel.pinCodeComponent },
                                      validationHandler = { validationHandler() }
    )
    dialog = SimpleDialog(options)
    dialog.init()
  }

  var validationHandler: () -> ValidationInfo? = { null }

  var okButtonHandler: () -> Boolean = { false }

  val disposable: Disposable
    get() = dialog.disposable

  val pinCodeComponent: JComponent
    get() = pairingPanel.pinCodeComponent

  val currentPinCode: String
    get() = pairingPanel.pinCode

  val isPinCodeValid: Boolean
    get() = currentPinCode.length == PinCodeInputPanel.PIN_CODE_DIGIT_COUNT

  fun createCenterPanel(): JComponent {
    // Set a preferred size so that the containing dialog shows big enough
    pairingPanel.component.preferredSize = panelPreferredSize
    return pairingPanel.component
  }

  fun show() {
    dialog.show()
  }

  fun setDevice(service: MdnsService) {
    pairingPanel.setDevice(service)
  }

  fun showPairingInProgress(text: String) {
    pairingPanel.showProgress(text)
    dialog.okButtonEnabled = false
    dialog.cancelButtonEnabled = false
  }

  fun showPairingSuccess(device: AdbOnlineDevice) {
    pairingPanel.showSuccess(device)
    dialog.okButtonEnabled = true
    dialog.cancelButtonEnabled = true
    dialog.okButtonText = "Done"
    dialog.cancelButtonVisible = false
  }

  fun showPairingError() {
    pairingPanel.showPairingError()
    dialog.okButtonEnabled = true
    dialog.cancelButtonEnabled = true
  }

  private val panelPreferredSize: JBDimension
    get() = JBDimension(500, 200)
}
