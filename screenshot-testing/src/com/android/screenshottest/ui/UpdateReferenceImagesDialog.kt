/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.screenshottest.ui

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.CheckboxTree
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * A dialog for selecting and viewing screenshot test previews. It features a two-pane layout with a
 * tree of previews on the left and a live-updating image viewer on the right.
 */
class UpdateReferenceImagesDialog(
  project: Project,
  private val logger: Logger = Logger.getInstance(UpdateReferenceImagesDialog::class.java),
) : DialogWrapper(project) {

  private val tree: CheckboxTree = CheckboxTree()
  private val previewsContainer = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }

  init {
    isModal = false
    title = "Add/Update Reference Images"
    setOKButtonText("Add")
    okAction.isEnabled = false // The "Add" button is disabled until an image loads.
    setCancelButtonText("Cancel")
    isResizable = true
    init()
  }

  fun onImageLoadCompleted() {
    okAction.isEnabled = true
  }

  override fun getDimensionServiceKey(): String {
    return "com.android.screenshottest.ui.UpdateReferenceImagesDialog"
  }

  override fun createCenterPanel(): JComponent {
    val splitter = JBSplitter(false, 0.3f)
    val treeScrollPane = JBScrollPane(tree)
    val rightScrollPane = JBScrollPane(previewsContainer)
    rightScrollPane.border = null
    splitter.firstComponent = treeScrollPane
    splitter.secondComponent = rightScrollPane
    val rootPanel = JPanel(BorderLayout())
    rootPanel.add(splitter, BorderLayout.CENTER)
    rootPanel.preferredSize = Dimension(800, 600)
    rootPanel.minimumSize = Dimension(550, 400)
    return rootPanel
  }

  override fun doOKAction() {
    close(OK_EXIT_CODE)
  }
}
