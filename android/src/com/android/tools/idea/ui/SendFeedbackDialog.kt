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

import com.android.tools.adtui.model.stdui.DefaultCommonTextFieldModel
import com.android.tools.adtui.model.stdui.EditingSupport
import com.android.tools.adtui.model.stdui.EditorCompletion
import com.android.tools.adtui.stdui.CommonTextField
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import kotlinx.collections.immutable.toImmutableList
import java.awt.Color
import java.awt.Desktop
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.RenderingHints
import java.nio.file.Path
import javax.swing.Action
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.event.HyperlinkEvent
import kotlin.io.path.name

private const val REPRO_TEXT = "Please enter the series of steps necessary to reproduce this bug."
private const val EXPECTED_TEXT = "What was the expected behavior?"
private const val ACTUAL_TEXT = "What was the actual behavior?"
private const val CONSENT_TEXT = "We may email your for more information or updates."
private const val PRIVACY_TEXT = "Information entered in the bug description will be publicly visible. To learn more about how Google uses your data, see <a href=\"http://www.google.com/policies/privacy/\">Google's Privacy Policy</a>."

class SendFeedbackDialog(project: Project?, file: Path?) : DialogWrapper(project) {
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

  private val feedbackComponents = CommonTextField(ComponentTextFieldModel()).apply {
    name = "feedbackComponents"
    preferredSize = Dimension(300, 40)
  }

  private val listModel = DefaultListModel<String>()
  private val listFiles = JBList(listModel).apply {
    preferredSize = Dimension(400, 300)
  }

  private val consentChkbox = JBCheckBox().apply {
    text = CONSENT_TEXT
  }

  private val grid = JPanel(GridBagLayout())

  // Set of all existing paths, to prevent duplicates
  private val pathSet = mutableSetOf<Path>()

  // List of paths sorted by file name, so that they match the order in the list control
  private val pathList = mutableListOf<Path>()

  val issueTitle: String
    get() = titleText.text

  val reproSteps: String
    get() = reproText.text
  val actual: String
    get() = actualText.text
  val expected: String
    get() = expectedText.text
  val component: String
    get() = feedbackComponents.text
  val paths: List<Path>
    get() = pathList.toImmutableList()

  init {
    title = "Create New Bug"
    isResizable = false
    isModal = true
    myOKAction.putValue(Action.NAME, "Submit")

    file?.let {
      pathSet.add(file)
      pathList.add(file)
      listModel.addElement(file.name)
    }

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

      val expectedLabel = JLabel().apply {
        text = "<html>Expected<br/>behavior:</html>"
      }

      constraints.gridy = 2

      val actualLabel = JLabel().apply {
        text = "<html>Actual<br/>behavior:</html>"
      }

      add(actualLabel, constraints)

      val componentLabel = JLabel().apply {
        text = "Component:"
      }

      constraints.gridy = 3

      add(expectedLabel, constraints)

      constraints.gridy = 4

      add(componentLabel, constraints)

      val filesLabel = JLabel().apply {
        text = "Attached Files:"
      }

      constraints.gridy = 5

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
      }

      add(feedbackComponents, constraints)

      constraints.apply {
        gridx = 1
        gridy = 5
        gridwidth = 2
        gridheight = 2
      }

      add(JScrollPane(listFiles), constraints)

      val addButton = JButton().apply {
        text = "Add"
        addActionListener { onAddButtonPressed() }
      }

      constraints.apply {
        gridwidth = 2
        gridheight = 1
        gridx = 3
        gridy = 5
      }

      add(addButton, constraints)

      val removeButton = JButton().apply {
        text = "Remove"
        addActionListener { onRemoveButtonPressed() }
      }

      constraints.apply {
        gridy = 6
      }

      add(removeButton, constraints)

      constraints.apply {
        gridwidth = 1
        gridheight = 1
        gridx = 1
        gridy = 7
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
        gridy = 8
      }

      add(privacy, constraints)
    }

    init()
  }

  private fun onAddButtonPressed() {
    val descriptor = FileChooserDescriptor(true, false, false, false, false, true)
    val fileChooserDialog = FileChooserFactory.getInstance().createFileChooser(descriptor, null, null)
    val files = fileChooserDialog.choose(null)

    for (path in files.map { it.toNioPath() }) {
      if (!pathSet.add(path)) {
        continue
      }

      // Insert the new element in the proper place to maintain sorting by file name
      val index = -(pathList.binarySearch(path, compareBy { it.name }) + 1)
      pathList.add(index, path)
      listModel.insertElementAt(path.name, index)
    }
  }

  private fun onRemoveButtonPressed() {
    val indices = listFiles.selectedIndices.reversed()
    for (index in indices) {
      val path = pathList[index]
      pathSet.remove(path)
      pathList.removeAt(index)
      listModel.removeElementAt(index)
    }
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

private class ComponentTextFieldModel : DefaultCommonTextFieldModel("", "Please select the affected component") {
  override val editingSupport = object : EditingSupport {
    override val completion: EditorCompletion = { FEEDBACK_COMPONENTS }
  }
}
