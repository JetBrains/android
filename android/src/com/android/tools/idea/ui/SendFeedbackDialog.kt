/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.ui

import com.android.tools.idea.diagnostics.report.DiagnosticsSummaryFileProvider
import com.android.tools.idea.diagnostics.report.FileInfo
import com.android.tools.idea.util.ZipData
import com.android.tools.idea.util.zipFiles
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.io.URLUtil
import java.awt.Color
import java.awt.Desktop
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.event.HyperlinkEvent
import javax.swing.tree.DefaultMutableTreeNode

private const val REPRO_TEXT = "Please enter the series of steps necessary to reproduce this bug."
private const val EXPECTED_TEXT = "What was the expected behavior?"
private const val ACTUAL_TEXT = "What was the actual behavior?"
private const val PRIVACY_TEXT = "Information entered in the bug description will be publicly visible. To learn more about how Google uses your data, see <a href=\"http://www.google.com/policies/privacy/\">Google's Privacy Policy</a>."
private const val UNKNOWN_VERSION = "Unknown"

private const val URL = "https://issuetracker.google.com/issues/new?component=192708&template=840533&foundIn=\$STUDIO_VERSION&format=MARKDOWN&title=\$TITLE&description=\$DESCR"

class SendFeedbackDialog(private val project: Project?, files: List<FileInfo>) : DialogWrapper(project) {
  private val titleText = JBTextField().apply {
    name = "titleText"
    preferredSize = Dimension(600, 40)
  }

  private val reproText = PlaceholderTextArea(REPRO_TEXT).apply {
    name = "reproText"
    preferredSize = Dimension(600, 150)
  }

  private val expectedText = PlaceholderTextArea(EXPECTED_TEXT).apply {
    name = "expectedText"
    preferredSize = Dimension(600, 150)
  }

  private val actualText = PlaceholderTextArea(ACTUAL_TEXT).apply {
    name = "actualText"
    preferredSize = Dimension(600, 150)
  }

  private val fileTree: Tree

  private val grid = JPanel(GridBagLayout())

  private val zipFilePath: String

  val issueTitle: String
    get() = titleText.text

  val reproSteps: String
    get() = reproText.text
  val actual: String
    get() = actualText.text
  val expected: String
    get() = expectedText.text

  init {
    title = "Create New Bug"
    isResizable = false
    isModal = true
    myOKAction.putValue(Action.NAME, "Submit")

    val datetime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
    val dir = DiagnosticsSummaryFileProvider.getDiagnosticsDirectoryPath(PathManager.getLogPath())
    zipFilePath = dir.resolve("DiagnosticsReport${datetime}.zip").toString()

    val titleLabel = JLabel().apply {
      text = "Title:"
    }

    grid.apply {
      val constraints = GridBagConstraints().apply {
        gridx = 0
        gridy = 0
        anchor = GridBagConstraints.NORTHWEST
        insets = Insets(10, 20, 0, 0)
      }

      add(titleLabel, constraints)

      val reproLabel = JLabel().apply {
        text = "Repro steps:"
      }

      constraints.gridy++

      add(reproLabel, constraints)

      val actualLabel = JLabel().apply {
        text = "<html>Actual<br/>behavior:</html>"
      }

      constraints.gridy++

      add(actualLabel, constraints)

      val expectedLabel = JLabel().apply {
        text = "<html>Expected<br/>behavior:</html>"
      }

      constraints.gridy++

      add(expectedLabel, constraints)

      val filesLabel = JLabel().apply {
        text = "Attached Files:"
      }
      constraints.gridy++

      add(filesLabel, constraints)

      constraints.apply {
        gridx = 1
        gridy = 0
      }

      add(titleText, constraints)

      constraints.gridy++

      add(reproText, constraints)

      constraints.gridy++

      add(actualText, constraints)

      constraints.gridy++

      add(expectedText, constraints)

      constraints.gridy++

      fileTree = buildTree(files)

      val scrollPane = JScrollPane(fileTree).apply {
        preferredSize = Dimension(600, 200)
      }

      add(scrollPane, constraints)

      val copyButton = JButton().apply {
        text = "Copy Path"
        addActionListener {
          Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(zipFilePath), null)
        }
      }

      constraints.gridy++

      add(copyButton, constraints)

      val privacy = JEditorPane("text/html", PRIVACY_TEXT).apply {
        isEditable = false
        background = Color(0, 0, 0, 0)
        preferredSize = Dimension(400, 80)
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)

        addHyperlinkListener { e ->
          if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
            if (Desktop.isDesktopSupported()) {
              Desktop.getDesktop().browse(e.url.toURI());
            }
          }
        }
      }

      constraints.gridy++

      add(privacy, constraints)
    }

    init()
  }

  override fun createCenterPanel(): JComponent = grid

  override fun doValidate(): ValidationInfo? {
    if (titleText.text.isNullOrBlank()) {
      return ValidationInfo("The title field cannot be empty.", titleText)
    }
    if (reproText.text.isNullOrBlank()) {
      return ValidationInfo("The repro steps field cannot be empty.", reproText)
    }
    if (actualText.text.isNullOrBlank()) {
      return ValidationInfo("The actual behavior field cannot be empty.", actualText)
    }
    return null
  }

  override fun doOKAction() {
    val list = buildList()
    val zipInfo = list.map { ZipData(it.source.toString(), it.destination.toString()) }.toTypedArray()
    zipFiles(zipInfo, zipFilePath)

    val url = URL.replace("\$TITLE", URLUtil.encodeURIComponent(this.issueTitle))
      .replace("\$STUDIO_VERSION", getVersion(ApplicationInfoEx.getInstanceEx()))

    com.intellij.ide.actions.SendFeedbackAction.submit(project, url, buildDescription())
  }

  private fun buildTree(list: List<FileInfo>): Tree {
    val root = FileTreeNode("Attached Files")

    for (file in list.sortedBy { it.destination.toString() }) {
      addFilesToTree(root, file)
    }

    return CheckboxTree(FileTreeRenderer(), root).apply {
      preferredSize = Dimension(300, 200)
    }
  }

  private fun addFilesToTree(root: DefaultMutableTreeNode, file: FileInfo) {
    val tokens = file.destination.toString().split('/')
    var current = root
    for (i in 0..tokens.size - 2) {
      val token = tokens[i]
      if (current.childCount > 0) {
        val lastChild = current.lastChild as DefaultMutableTreeNode
        if (lastChild.userObject as String == token) {
          current = lastChild
          continue
        }
      }
      val newNode = FileTreeNode(token)
      current.add(newNode)
      current = newNode
    }

    current.add(FileTreeNode(tokens.last(), file))
  }
  private fun buildList(): List<FileInfo> {
    val root = fileTree.model.root as FileTreeNode
    val list = mutableListOf<FileInfo>()
    addFilesToList(root, list)
    return list
  }

  private fun addFilesToList(node: FileTreeNode, list: MutableList<FileInfo>) {
    if (node.isChecked) {
      node.fileInfo?.let { list.add(it) }
    }

    for (child in node.children()) {
      addFilesToList(child as FileTreeNode, list)
    }
  }

  private fun getVersion(applicationInfo: ApplicationInfoEx): String {
    val major = applicationInfo.majorVersion ?: return UNKNOWN_VERSION
    val minor = applicationInfo.minorVersion ?: return UNKNOWN_VERSION
    val micro = applicationInfo.microVersion ?: return UNKNOWN_VERSION
    val patch = applicationInfo.patchVersion ?: return UNKNOWN_VERSION
    return java.lang.String.join(".", major, minor, micro, patch)
  }

  private fun buildDescription(): String {
    val blocks = mutableListOf(buildBlock("Repro Steps", reproSteps), buildBlock("Actual Behavior", actual))
    if (expected.isNotEmpty()) {
      blocks.add(buildBlock("Expected Behavior", expected))
    }

    return blocks.joinToString("\n\n", "```\n", "\n```")
  }

  private fun buildBlock(title: String, content: String): String {
    return "$title:\n$content"
  }

  class FileTreeRenderer : CheckboxTree.CheckboxTreeCellRenderer() {
    override fun customizeRenderer(tree: JTree?,
                                   value: Any?,
                                   selected: Boolean,
                                   expanded: Boolean,
                                   leaf: Boolean,
                                   row: Int,
                                   hasFocus: Boolean) {
      (value as? DefaultMutableTreeNode)?.let {
        textRenderer.append(it.userObject as String)
      }

      super.customizeRenderer(tree, value, selected, expanded, leaf, row, hasFocus)
    }
  }

  class FileTreeNode(userObject: String, val fileInfo: FileInfo? = null) : CheckedTreeNode(userObject)
}

private class PlaceholderTextArea(private val placeHolderText: String?) : JBTextArea() {
  public override fun paintComponent(g: Graphics) {
    super.paintComponent(g)

    placeHolderText ?: return
    if (super.getText().isNotEmpty()) {
      return
    }

    val g2 = g as Graphics2D

    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2.color = super.getDisabledTextColor()

    val metrics = g2.fontMetrics
    val x = insets.left + margin.left
    val y = insets.top + margin.top + metrics.ascent

    g2.drawString(placeHolderText, x, y)
  }
}
