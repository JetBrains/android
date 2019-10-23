/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcemanager.importer

import com.android.tools.idea.ui.resourcemanager.qualifiers.QualifierConfigurationPanel
import com.android.tools.idea.ui.resourcemanager.widget.ChessBoardPanel
import com.android.tools.idea.ui.resourcemanager.widget.Separator
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JPanel

private val PREVIEW_SIZE = JBUI.size(100)

class FileImportRow(val viewModel: FileImportRowViewModel) : JPanel(BorderLayout()) {

  val preview = JBLabel().apply {
    horizontalAlignment = JBLabel.CENTER
  }

  private val previewWrapper = ChessBoardPanel(BorderLayout()).apply {
    preferredSize = PREVIEW_SIZE
    maximumSize = PREVIEW_SIZE
    border = JBUI.Borders.customLine(JBColor.border(), 1)
    add(preview)
  }

  private val fileName = JBLabel(viewModel.fileName)
  private val folderConfiguration = JBLabel(viewModel.qualifiers)
  private val fileSize = JBLabel(viewModel.fileSize)
  private val fileDimension = JBLabel(viewModel.fileDimension)

  private val doNotImportButton = LinkLabel<Any?>("Do not import", null) { _, _ -> removeButtonClicked() }.apply {
    isFocusable = true
  }

  private fun removeButtonClicked() {
    parent.let {
      it.remove(this)
      it.revalidate()
      it.repaint()
    }
    viewModel.removeFile()
  }

  private val middlePane = JPanel(BorderLayout()).apply {
    add(JPanel(FlowLayout(FlowLayout.LEFT, 5, 0)).apply {
      add(fileName)
      add(separator())
      add(folderConfiguration)
      add(separator())
      add(fileSize)
      if (fileDimension.text.isNotBlank()) {
        add(separator())
        add(fileDimension)
      }
    }, BorderLayout.WEST)
    add(doNotImportButton, BorderLayout.EAST)

    add(QualifierConfigurationPanel(viewModel.qualifierViewModel), BorderLayout.SOUTH)
  }

  private fun separator() = Separator(8, 4)

  init {
    add(JPanel().apply {
      add(previewWrapper)
    }, BorderLayout.WEST)
    add(middlePane)
    border = BorderFactory.createCompoundBorder(
      JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0),
      JBUI.Borders.empty(0, 4, 2, 4))
    viewModel.updateCallback = ::update
    update()
  }

  fun update() {
    fileName.text = viewModel.fileName
    folderConfiguration.text = viewModel.qualifiers
    fileSize.text = viewModel.fileSize
    fileDimension.text = viewModel.fileDimension
    repaint()
  }
}
