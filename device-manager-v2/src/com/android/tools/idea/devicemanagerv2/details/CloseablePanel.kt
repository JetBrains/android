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
package com.android.tools.idea.devicemanagerv2.details

import com.android.tools.adtui.categorytable.IconButton
import com.android.tools.adtui.categorytable.constrainSize
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBDimension
import icons.StudioIcons
import java.awt.event.ActionListener
import javax.swing.GroupLayout
import javax.swing.JComponent
import javax.swing.LayoutStyle

/** A panel with a heading and a close button above a content panel. */
internal open class CloseablePanel(heading: String, component: JComponent) :
  JBPanel<CloseablePanel>() {
  private val headingLabel = headingLabel(heading)
  val closeButton =
    IconButton(StudioIcons.Common.CLOSE).apply { constrainSize(JBDimension(22, 22)) }

  init {
    val layout = GroupLayout(this)

    val horizontalGroup =
      layout
        .createParallelGroup()
        .addGroup(
          layout
            .createSequentialGroup()
            .addContainerGap()
            .addComponent(headingLabel)
            .addPreferredGap(
              LayoutStyle.ComponentPlacement.UNRELATED,
              GroupLayout.DEFAULT_SIZE,
              Int.MAX_VALUE,
            )
            .addComponent(closeButton)
            .addContainerGap()
        )
        .addComponent(component)

    val verticalGroup =
      layout
        .createSequentialGroup()
        .addContainerGap()
        .addGroup(
          layout
            .createParallelGroup(GroupLayout.Alignment.CENTER)
            .addComponent(headingLabel)
            .addComponent(closeButton)
        )
        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(component)

    layout.setHorizontalGroup(horizontalGroup)
    layout.setVerticalGroup(verticalGroup)

    this.layout = layout
  }

  fun addCloseActionListener(listener: ActionListener) {
    closeButton.addActionListener(listener)
  }
}
