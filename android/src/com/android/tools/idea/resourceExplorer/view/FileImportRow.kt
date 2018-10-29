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
package com.android.tools.idea.resourceExplorer.view

import com.android.tools.adtui.ui.ClickableLabel
import com.android.tools.idea.resourceExplorer.viewmodel.FileImportRowViewModel
import com.android.tools.idea.resourceExplorer.widget.ChessBoardPanel
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JPanel
import javax.swing.SwingConstants

private val PREVIEW_SIZE = JBUI.size(150)

class FileImportRow(val viewModel: FileImportRowViewModel) : JPanel(BorderLayout()) {

  private val preview = JBLabel().apply {
    preferredSize = PREVIEW_SIZE
  }
  private val previewWrapper = ChessBoardPanel(BorderLayout()).apply {
    preferredSize = PREVIEW_SIZE
    add(preview)
  }

  private val fileName = JBLabel(viewModel.fileName)
  private val folderConfiguration = JBLabel(viewModel.qualifiers)
  private val fileSize = JBLabel(viewModel.fileSize)
  private val fileDimension = JBLabel(viewModel.fileDimension)

  private val doNotImportButton = ClickableLabel("Do not import", StudioIcons.Common.CLOSE, SwingConstants.LEFT)

  private val middlePane = JPanel(BorderLayout()).apply {
    add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
      add(fileName)
      add(folderConfiguration)
      add(fileSize)
      add(fileDimension)
      add(doNotImportButton)
    }, BorderLayout.NORTH)

    add(QualifierConfigurationPanel(viewModel.qualifierViewModel))
  }

  init {
    add(previewWrapper, BorderLayout.WEST)
    add(middlePane)
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
