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

import com.android.tools.adtui.categorytable.TableComponent
import com.android.tools.adtui.categorytable.TablePresentation
import com.android.tools.adtui.categorytable.TablePresentationManager
import com.android.tools.adtui.categorytable.applyColors
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.UIUtil
import javax.swing.GroupLayout

/** A pair of labels arranged vertically, with the bottom one in a smaller, lighter font. */
class TwoLineLabel : JBPanel<TwoLineLabel>(null), TableComponent {
  internal val line1Label = JBLabel()
  internal val line2Label = JBLabel()

  init {
    isOpaque = false

    val layout = GroupLayout(this)
    val horizontalGroup =
      layout
        .createParallelGroup()
        .addComponent(line1Label, 0, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
        .addComponent(line2Label, 0, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)

    val verticalGroup =
      layout.createSequentialGroup().addComponent(line1Label).addComponent(line2Label)

    layout.setHorizontalGroup(horizontalGroup)
    layout.setVerticalGroup(verticalGroup)

    line2Label.font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)

    this.layout = layout
  }

  override fun updateTablePresentation(
    manager: TablePresentationManager,
    presentation: TablePresentation
  ) {
    presentation.applyColors(this)
    manager.applyPresentation(line1Label, presentation)
    manager.applyPresentation(
      line2Label,
      presentation.copy(
        foreground =
          when {
            presentation.rowSelected -> presentation.foreground.lighten()
            else -> LINE2_COLOR
          }
      )
    )
  }

  companion object {
    val LINE2_COLOR = JBColor(0x818594, 0x6F737A)
  }
}
