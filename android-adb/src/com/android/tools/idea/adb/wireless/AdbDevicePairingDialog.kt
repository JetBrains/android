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
import com.android.tools.idea.ui.AbstractDialogWrapper
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.ui.JBDimension
import javax.swing.JComponent

@UiThread
class AdbDevicePairingDialog(project: Project, canBeParent: Boolean, ideModalityType: DialogWrapper.IdeModalityType) {
  private val dialogWrapper = AbstractDialogWrapper.factory.createDialogWrapper(project, canBeParent, ideModalityType)
  private val pairingPanel = AdbDevicePairingPanel()

  init {
    dialogWrapper.centerPanelProvider = { createCenterPanel() }
    dialogWrapper.isModal = true
    dialogWrapper.title = "Pair devices for wireless debugging"
    dialogWrapper.okButtonText = "Close"
    dialogWrapper.init()
  }

  val disposable: Disposable
    get() = dialogWrapper.disposable

  fun createCenterPanel(): JComponent {
    // Set a preferred size so that the containing dialog shows big enough
    pairingPanel.rootComponent.preferredSize = panelPreferredSize
    return pairingPanel.rootComponent
  }

  fun show() {
    dialogWrapper.show()
  }

  fun setQrCodeImage(qrCodeImage: QrCodeImage) {
    pairingPanel.setQrCodeImage(qrCodeImage)
  }

  fun showQrCodeStatus(label: String) {
    pairingPanel.setQrCodePairingStatus(label)
  }

  private val panelPreferredSize: JBDimension
    get() = JBDimension(1000, 600)
}
