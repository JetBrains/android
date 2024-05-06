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
package com.android.tools.idea.appinspection.inspectors.network.view.details

import com.android.tools.adtui.common.AdtUiUtils
import com.intellij.util.ui.JBUI
import java.awt.CardLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/** A [JComponent] that can switch between 2 provided child components */
internal class SwitchingPanel(
  component1: JComponent,
  title1: String,
  component2: JComponent,
  title2: String,
) : JPanel(null) {
  val switcher = JLabel()

  init {
    val cardLayout = CardLayout()
    layout = cardLayout

    component1.name = title1
    component2.name = title2
    add(component1, title1)
    add(component2, title2)

    switcher.text = title2

    val hoverColor = AdtUiUtils.overlayColor(switcher.background.rgb, switcher.foreground.rgb, 0.9f)
    val defaultColor = AdtUiUtils.overlayColor(switcher.background.rgb, hoverColor.rgb, 0.6f)
    switcher.foreground = defaultColor
    switcher.border = JBUI.Borders.empty(0, 10, 0, 5)
    switcher.addMouseListener(
      object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          switcher.text = if (title1 == switcher.text) title2 else title1
          cardLayout.next(this@SwitchingPanel)
        }

        override fun mouseEntered(e: MouseEvent) {
          switcher.foreground = hoverColor
        }

        override fun mouseExited(e: MouseEvent) {
          switcher.foreground = defaultColor
        }
      }
    )
  }
}
