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
package com.android.tools.idea.ui.resourcemanager.widget

import com.android.tools.idea.ui.resourcemanager.getTestDataDirectory
import com.google.common.truth.Truth
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.junit.Test
import java.awt.BorderLayout
import javax.swing.ImageIcon
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import kotlin.test.assertEquals


class SectionListTest {

  @Suppress("UndesirableClassUsage")
  @Test
  fun testIndices() {
    val model = SectionListModel()
    val sectionList = SectionList(model)
    val list1 = JList<String>(arrayOf("1", "2", "3"))
    val list2 = JList<String>(arrayOf("4", "5", "6"))
    model.addSection(SimpleSection("Section 1", list1))
    model.addSection(SimpleSection("Section 2", list2))
    sectionList.selectedIndex = 0 to 0
    assertEquals(0, sectionList.getLists()[0].selectedIndex)

    val selection1 = IntArray(3).apply {
      set(0, 2)
      set(1, 0)
      set(2, 1)
    }

    val selection2 = IntArray(1) { 1 }

    sectionList.selectedIndices = listOf(selection1, null)
    Truth.assertThat(sectionList.selectedIndices[0]).asList().containsExactly(0, 1, 2)
    Truth.assertThat(sectionList.selectedIndices[1]).isEmpty()
    assertEquals(0, sectionList.getLists()[0].selectedIndex)
    assertEquals(-1, sectionList.getLists()[1].selectedIndex)

    sectionList.selectedIndices = listOf(selection1, selection2)
    Truth.assertThat(sectionList.selectedIndices[0]).asList().containsExactly(0, 1, 2)
    Truth.assertThat(sectionList.selectedIndices[1]).asList().containsExactly(1)
    assertEquals(0, sectionList.getLists()[0].selectedIndex)
    assertEquals(1, sectionList.getLists()[1].selectedIndex)
  }
}

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
  panel.add(sectionList)
  panel.add(sectionList.sectionsComponent, BorderLayout.WEST)

  frame.contentPane = panel
  frame.pack()
  frame.isVisible = true
}