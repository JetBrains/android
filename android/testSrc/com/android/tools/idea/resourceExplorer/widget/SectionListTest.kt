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
package com.android.tools.idea.resourceExplorer.widget

import com.android.tools.idea.resourceExplorer.getTestDataDirectory
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.*

fun main(args: Array<String>) {
  val frame = JFrame()
  val panel = JPanel(BorderLayout())
  val listModel = SectionListModel()
  val imageIcon = ImageIcon(getTestDataDirectory() + "/success-icons/success@2x.png")

  for (i in 1..10) {
    listModel.addSection(
      SimpleSection<String>(
        "Section $i",

        JList(
          1.rangeTo(10).map { "Element $it" }.toTypedArray()
        ).also {
          it.layoutOrientation = JList.HORIZONTAL_WRAP
          it.setCellRenderer { list, value, index, isSelected, cellHasFocus ->
            JLabel(value, imageIcon, JLabel.CENTER).apply {
              isOpaque = true
              if (isSelected) {
                background = UIUtil.getListBackground(isSelected)
              }
            }
          }
        }
      )
    )
  }

  val sectionList = SectionList(listModel)
  panel.preferredSize = JBUI.size(1000, 1000)
  panel.add(sectionList.mainComponent)
  panel.add(sectionList.sectionsComponent, BorderLayout.WEST)

  frame.contentPane = panel
  frame.pack()
  frame.isVisible = true
}