/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.wearparing

import com.android.tools.idea.wizard.model.ModelWizardStep
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI.Borders.empty
import com.intellij.util.ui.UIUtil.ComponentStyle.LARGE
import icons.StudioIcons
import java.awt.GridBagConstraints
import java.awt.GridBagConstraints.HORIZONTAL
import java.awt.GridBagConstraints.NONE
import java.awt.GridBagConstraints.REMAINDER
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JPanel

class NewConnectionAlertStep(
  model: WearDevicePairingModel,
  val project: Project
) : ModelWizardStep<WearDevicePairingModel>(model, "") {

  override fun shouldShow(): Boolean {
    val (pairedPhone, pairedWear) = WearPairingManager.getKeepForwardAlive()
    val selectedPhone = model.phoneDevice.valueOrNull
    val selectedWear = model.wearDevice.valueOrNull
    return pairedPhone != null && pairedWear != null && selectedPhone != null && selectedWear != null &&
           (pairedPhone.deviceID != selectedPhone.deviceID || pairedWear.deviceID != selectedWear.deviceID)
  }

  override fun getComponent(): JComponent = JPanel().apply {
    layout = GridBagLayout()
    border = empty(24)

    val (pairedPhone, pairedWear) = WearPairingManager.getKeepForwardAlive()
    val selectedPhone = model.phoneDevice.value
    val selectedWear = model.wearDevice.value

    add(
      JBLabel("Disconnecting existing devices", LARGE).withFont(JBFont.label().asBold()).withBorder(empty(0, 0, 24, 0)),
      gridConstraint(x = 0, y = 0, fill = HORIZONTAL, gridwidth = REMAINDER)
    )
    add(
      JBLabel(IconUtil.scale(StudioIcons.Common.WARNING, null, 2f)).withBorder(empty(0, 0, 0, 8)),
      gridConstraint(x = 0, y = 1)
    )
    add(
      JBLabel("<html>Creating a new connection between ${selectedWear.displayName} and ${selectedPhone.displayName} " +
              "to enable communication.</html>").withBorder(empty(0, 0, 24, 0)),
      gridConstraint(x = 1, y = 1, weightx = 1.0, fill = HORIZONTAL)
    )
    add(
      JBLabel("<html>This  will disconnect ${pairedWear?.displayName} from ${pairedPhone?.displayName}.</html>"),
      gridConstraint(x = 1, y = 2, weightx = 1.0, fill = HORIZONTAL)
    )

    // Bottom padding
    add(JPanel(), gridConstraint(x = 0, y = 3, weighty = 1.0))
  }
}

internal fun gridConstraint(
  x: Int, y: Int, weightx: Double = 0.0, weighty: Double = 0.0, fill: Int = NONE, gridwidth: Int = 1): GridBagConstraints =
  GridBagConstraints().apply {
    this.gridx = x
    this.gridy = y
    this.weightx = weightx
    this.weighty = weighty
    this.fill = fill
    this.gridwidth = gridwidth
    this.anchor = GridBagConstraints.NORTH
  }