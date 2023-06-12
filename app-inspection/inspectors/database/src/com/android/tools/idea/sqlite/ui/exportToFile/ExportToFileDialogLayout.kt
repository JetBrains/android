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
package com.android.tools.idea.sqlite.ui.exportToFile

import com.intellij.util.ui.JBUI
import javax.swing.GroupLayout
import javax.swing.GroupLayout.Alignment.LEADING
import javax.swing.GroupLayout.DEFAULT_SIZE
import javax.swing.GroupLayout.PREFERRED_SIZE
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.LayoutStyle
import javax.swing.LayoutStyle.ComponentPlacement.RELATED
import kotlin.Short.Companion.MAX_VALUE

/** Creates a layout for [ExportToFileDialogView] */
internal object ExportToFileDialogLayout {
  fun createLayout(
    fileTypeLabel: JComponent,
    formatDbRadioButton: JComponent,
    formatSqlRadioButton: JComponent,
    formatCsvRadioButton: JComponent,
    delimiterLabel: JComponent,
    delimiterComboBox: JComponent,
    saveLocationLabel: JComponent,
    saveLocationTextField: JComponent
  ): JComponent {

    // panels to position elements
    val fileFormatPanel = JPanel()
    val delimiterPanel = JPanel()
    val saveLocationPanel = JPanel()
    val mainContentPanel = JPanel()
    val result = JPanel()

    // file format selection
    val fileFormatPanelLayout = GroupLayout(fileFormatPanel)
    fileFormatPanel.layout = fileFormatPanelLayout
    fileFormatPanelLayout.setHorizontalGroup(
      fileFormatPanelLayout
        .createParallelGroup(LEADING)
        .addComponent(fileTypeLabel, DEFAULT_SIZE, DEFAULT_SIZE, MAX_VALUE.toInt())
        .addGroup(
          fileFormatPanelLayout
            .createSequentialGroup()
            .addGap(20.scaled)
            .addGroup(
              fileFormatPanelLayout
                .createParallelGroup(LEADING)
                .addComponent(formatCsvRadioButton)
                .addComponent(formatSqlRadioButton)
                .addComponent(formatDbRadioButton)
            )
        )
        .addGroup(
          fileFormatPanelLayout
            .createSequentialGroup()
            .addGap(40.scaled)
            .addComponent(delimiterPanel, DEFAULT_SIZE, DEFAULT_SIZE, MAX_VALUE.toInt())
        )
    )
    fileFormatPanelLayout.setVerticalGroup(
      fileFormatPanelLayout
        .createParallelGroup(LEADING)
        .addGroup(
          fileFormatPanelLayout
            .createSequentialGroup()
            .addComponent(fileTypeLabel)
            .addPreferredGap(RELATED)
            .addComponent(formatDbRadioButton)
            .addPreferredGap(RELATED)
            .addComponent(formatSqlRadioButton)
            .addPreferredGap(RELATED)
            .addComponent(formatCsvRadioButton)
            .addPreferredGap(RELATED)
            .addComponent(delimiterPanel, PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
            .addGap(0)
        )
    )

    // delimiter selection
    val delimiterPanelLayout = GroupLayout(delimiterPanel)
    delimiterPanel.layout = delimiterPanelLayout
    delimiterPanelLayout.setHorizontalGroup(
      delimiterPanelLayout
        .createParallelGroup(LEADING)
        .addGroup(
          delimiterPanelLayout
            .createSequentialGroup()
            .addComponent(delimiterLabel)
            .addPreferredGap(RELATED)
            .addComponent(delimiterComboBox, 0, DEFAULT_SIZE, MAX_VALUE.toInt())
            .addGap(0)
        )
    )
    delimiterPanelLayout.setVerticalGroup(
      delimiterPanelLayout
        .createParallelGroup(LEADING)
        .addGroup(
          delimiterPanelLayout
            .createSequentialGroup()
            .addGroup(
              delimiterPanelLayout
                .createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(delimiterLabel)
                .addComponent(delimiterComboBox, PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
            )
            .addGap(0)
        )
    )

    // destination file selection
    val saveLocationPanelLayout = GroupLayout(saveLocationPanel)
    saveLocationPanel.layout = saveLocationPanelLayout
    saveLocationPanelLayout.setHorizontalGroup(
      saveLocationPanelLayout
        .createParallelGroup(LEADING)
        .addGroup(
          saveLocationPanelLayout
            .createSequentialGroup()
            .addComponent(saveLocationLabel)
            .addPreferredGap(RELATED)
            .addComponent(saveLocationTextField, DEFAULT_SIZE, 275.scaled, MAX_VALUE.toInt())
            .addGap(0)
        )
    )
    saveLocationPanelLayout.setVerticalGroup(
      saveLocationPanelLayout
        .createParallelGroup(LEADING)
        .addGroup(
          saveLocationPanelLayout
            .createSequentialGroup()
            .addGroup(
              saveLocationPanelLayout
                .createParallelGroup(GroupLayout.Alignment.CENTER)
                .addComponent(saveLocationLabel)
                .addComponent(saveLocationTextField, PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
            )
            .addGap(0)
        )
    )

    // putting the above together
    val mainContentPanelLayout = GroupLayout(mainContentPanel)
    mainContentPanel.layout = mainContentPanelLayout
    mainContentPanelLayout.setHorizontalGroup(
      mainContentPanelLayout
        .createParallelGroup(LEADING)
        .addGroup(
          mainContentPanelLayout
            .createSequentialGroup()
            .addGroup(
              mainContentPanelLayout
                .createParallelGroup(LEADING)
                .addComponent(saveLocationPanel, DEFAULT_SIZE, DEFAULT_SIZE, MAX_VALUE.toInt())
                .addComponent(fileFormatPanel, DEFAULT_SIZE, DEFAULT_SIZE, MAX_VALUE.toInt())
            )
            .addGap(0)
        )
    )
    mainContentPanelLayout.setVerticalGroup(
      mainContentPanelLayout
        .createParallelGroup(LEADING)
        .addGroup(
          mainContentPanelLayout
            .createSequentialGroup()
            .addComponent(fileFormatPanel, PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
            .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
            .addComponent(saveLocationPanel, PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
        )
    )

    // adding padding
    val layout = GroupLayout(result)
    result.layout = layout
    layout.setHorizontalGroup(
      layout
        .createParallelGroup(LEADING)
        .addGroup(
          GroupLayout.Alignment.TRAILING,
          layout
            .createSequentialGroup()
            .addGap(20.scaled)
            .addComponent(mainContentPanel, DEFAULT_SIZE, DEFAULT_SIZE, MAX_VALUE.toInt())
            .addGap(20.scaled)
        )
    )
    layout.setVerticalGroup(
      layout
        .createParallelGroup(LEADING)
        .addGroup(
          layout
            .createSequentialGroup()
            .addGap(20.scaled)
            .addComponent(mainContentPanel, PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE)
            .addGap(20.scaled)
        )
    )

    // done
    return result
  }

  private val Int.scaled
    get() = JBUI.scale(this)
}
