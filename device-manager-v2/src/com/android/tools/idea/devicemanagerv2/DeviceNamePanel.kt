/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.devicemanagerv2

import com.android.sdklib.AndroidVersion
import com.android.sdklib.getReleaseNameAndDetails
import com.android.tools.adtui.categorytable.TableComponent
import com.android.tools.adtui.categorytable.TablePresentation
import com.android.tools.adtui.categorytable.TablePresentationManager
import com.android.tools.idea.wearpairing.WearPairingManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Color
import javax.swing.GroupLayout
import javax.swing.LayoutStyle.ComponentPlacement
import kotlin.math.min

/**
 * A panel that renders the name of the device, along with its wear pairing status and a second line
 * to indicate more details, such as its Android version or an error state.
 */
internal class DeviceNamePanel(private val wearPairingManager: WearPairingManager) :
  JBPanel<DeviceNamePanel>(null), TableComponent {

  internal val nameLabel = JBLabel()
  internal val line2Label = JBLabel()
  private val pairedLabel = JBLabel()

  init {
    isOpaque = false

    val layout = GroupLayout(this)
    val horizontalGroup =
      layout
        .createSequentialGroup()
        .addPreferredGap(ComponentPlacement.RELATED)
        .addGroup(
          layout
            .createParallelGroup()
            .addComponent(nameLabel, 0, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            .addComponent(line2Label, 0, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
        )
        .addPreferredGap(ComponentPlacement.RELATED, GroupLayout.DEFAULT_SIZE, Int.MAX_VALUE)
        .addComponent(pairedLabel)
        .addGap(JBUI.scale(4))

    val verticalGroup =
      layout
        .createParallelGroup(GroupLayout.Alignment.CENTER)
        .addGroup(
          layout
            .createSequentialGroup()
            .addContainerGap(GroupLayout.DEFAULT_SIZE, Int.MAX_VALUE)
            .addComponent(nameLabel)
            .addComponent(line2Label)
            .addContainerGap(GroupLayout.DEFAULT_SIZE, Int.MAX_VALUE)
        )
        .addComponent(pairedLabel)

    layout.setHorizontalGroup(horizontalGroup)
    layout.setVerticalGroup(verticalGroup)

    line2Label.font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)

    this.layout = layout
  }

  fun update(deviceRowData: DeviceRowData) {
    nameLabel.text = deviceRowData.name
    line2Label.text = deviceRowData.toLine2Text()

    // TODO: Update pairedLabel
  }

  private fun DeviceRowData.toLine2Text() =
    when (androidVersion) {
      null -> ""
      else -> androidVersion.toLabelText() + (abi?.cpuArch?.let { " | $it" } ?: "")
    }

  private fun AndroidVersion.toLabelText(): String {
    val (name, details) = getReleaseNameAndDetails(includeCodeName = true)
    return name + (details?.let { " ($details)" } ?: "")
  }

  override fun updateTablePresentation(
    manager: TablePresentationManager,
    presentation: TablePresentation
  ) {
    manager.applyPresentation(nameLabel, presentation)
    manager.applyPresentation(
      line2Label,
      presentation.copy(foreground = presentation.foreground.lighten())
    )
  }
}

/**
 * Makes this color closer to the background color (lighter in light theme, darker in dark theme).
 */
@VisibleForTesting
internal fun Color.lighten() =
  JBColor.lazy {
    // Color.brigher() on black only takes us from 0x000000 to 0x030303; even +50 is rather subtle.
    val red = min(red + 50, 255)
    val green = min(green + 50, 255)
    val blue = min(blue + 50, 255)
    JBColor(Color(red, green, blue), darker())
  }
