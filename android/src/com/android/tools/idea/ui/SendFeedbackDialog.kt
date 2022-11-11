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

import com.android.tools.idea.diagnostics.report.FileInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.treeStructure.Tree
import java.awt.Color
import java.awt.Desktop
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.RenderingHints
import java.nio.file.Path
import java.util.Arrays
import java.util.Collections
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.event.HyperlinkEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.MutableTreeNode
import javax.swing.tree.TreeNode

private const val REPRO_TEXT = "Please enter the series of steps necessary to reproduce this bug."
private const val EXPECTED_TEXT = "What was the expected behavior?"
private const val ACTUAL_TEXT = "What was the actual behavior?"
private const val CONSENT_TEXT = "We may email your for more information or updates."
private const val PRIVACY_TEXT = "Information entered in the bug description will be publicly visible. To learn more about how Google uses your data, see <a href=\"http://www.google.com/policies/privacy/\">Google's Privacy Policy</a>."
private const val DATA_TEXT = "<a href=\"http://www.google.com/policies/privacy/\">See what information we collect.</a>"

class SendFeedbackDialog(project: Project?, files: List<FileInfo>) : DialogWrapper(project) {
  private val titleText = JBTextField().apply {
    name = "titleText"
    preferredSize = Dimension(800, 40)
  }

  private val reproText = PlaceholderTextArea(REPRO_TEXT).apply {
    name = "reproText"
    preferredSize = Dimension(800, 200)
  }

  private val expectedText = PlaceholderTextArea(EXPECTED_TEXT).apply {
    name = "expectedText"
    preferredSize = Dimension(800, 200)
  }

  private val actualText = PlaceholderTextArea(ACTUAL_TEXT).apply {
    name = "actualText"
    preferredSize = Dimension(800, 200)
  }

  private val consentChkbox = JBCheckBox().apply {
    text = CONSENT_TEXT
  }

  private val grid = JPanel(GridBagLayout())

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

      constraints.gridy = 1

      add(reproLabel, constraints)

      val actualLabel = JLabel().apply {
        text = "<html>Actual<br/>behavior:</html>"
      }

      constraints.gridy = 2

      add(actualLabel, constraints)

      val expectedLabel = JLabel().apply {
        text = "<html>Expected<br/>behavior:</html>"
      }

      constraints.gridy = 3

      add(expectedLabel, constraints)

      val filesLabel = JLabel().apply {
        text = "Attached Files:"
      }
      constraints.gridy = 4

      add(filesLabel, constraints)

      constraints.apply {
        gridx = 1
        gridy = 0
        gridwidth = 3
      }

      add(titleText, constraints)

      constraints.apply {
        gridy = 1
      }

      add(reproText, constraints)

      constraints.apply {
        gridy = 2
      }

      add(actualText, constraints)

      constraints.apply {
        gridy = 3
      }

      add(expectedText, constraints)

      constraints.apply {
        gridx = 1
        gridy = 4
        gridwidth = 2
        gridheight = 2
      }

      val scrollPane = JScrollPane(buildTree(files)).apply {
        preferredSize = Dimension(300, 200)
      }

      add(JScrollPane(buildTree(files)), constraints)

      val addButton = JButton().apply {
        text = "Add"
      }

      constraints.apply {
        gridwidth = 2
        gridheight = 1
        gridx = 3
        gridy = 4
      }

      add(addButton, constraints)

      val removeButton = JButton().apply {
        text = "Remove"
      }

      constraints.apply {
        gridy = 5
      }

      add(removeButton, constraints)

      constraints.apply {
        gridwidth = 1
        gridheight = 1
        gridx = 1
        gridy = 6
      }

      add(consentChkbox, constraints)

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

      constraints.apply {
        gridy = 7
      }

      add(privacy, constraints)

      val data = JEditorPane("text/html", DATA_TEXT).apply {
        isEditable = false
        background = Color(0, 0, 0, 0)
        preferredSize = Dimension(400, 20)
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)

        addHyperlinkListener { e ->
          if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
            if (Desktop.isDesktopSupported()) {
              Desktop.getDesktop().browse(e.url.toURI());
            }
          }
        }
      }

      constraints.apply {
        gridy = 8
      }

      add(data, constraints)
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

  private fun buildTree(list: List<FileInfo>) : Tree {
    val root = FileNode("Attached Files")

    for(file in list) {
      addFile(root, file)
    }

    return Tree(root).apply {
      preferredSize = Dimension(300, 200)
    }
  }

  private fun addFile(root: FileNode, file: FileInfo) {
    val tokens = file.destination.toString().split('/')
    var current = root
    for (i in 0..tokens.size - 2) {
      current = current.addNode(tokens[i], null)
    }
    current.addNode(tokens[tokens.size - 1], file.source)
  }

  private class FileNode(name: String, path: Path? = null) : DefaultMutableTreeNode(name, path == null) {
    fun addNode(name: String, path: Path?) : FileNode {
      val node = FileNode(name, path)
      if (this.children == null) {
        this.add(node)
        return node
      }

      val index = Collections.binarySearch(this.children, node, NodeComparator())
      if (index >= 0) {
        return this.children.get(index) as FileNode
      }

      this.insert(node, -index - 1)
      return node
    }
  }

  private class NodeComparator : Comparator<TreeNode> {
    override fun compare(o1: TreeNode?, o2: TreeNode?): Int {
      val node1 = o1 as FileNode
      val node2 = o2 as FileNode

      if (node1.allowsChildren != node2.allowsChildren) {
        return node1.allowsChildren.compareTo(node2.allowsChildren)
      }

      val name1 = node1.userObject as String
      val name2 = node2.userObject as String

      return name1.compareTo(name2)
    }
  }
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
