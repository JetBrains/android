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
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import org.jetbrains.annotations.Nullable
import org.jetbrains.kotlin.ir.interpreter.contractsDslAnnotation
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.RenderingHints
import java.awt.event.ActionEvent
import java.nio.file.Path
import javax.swing.Action
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField

private const val REPRO_TEXT = "Please enter the series of steps necessary to reproduce this bug."
private const val EXPECTED_TEXT = "What was the expected behavior?"
private const val ACTUAL_TEXT = "What was the actual behavior?"

class SendFeedbackDialog(project: Project?, file: Path, user: String) : DialogWrapper(project) {
  private val titleText = JBTextField().apply {
    name = "titleText"
    preferredSize = Dimension(800, 40)
  }

  private val reproText = PlaceholderTextArea(REPRO_TEXT).apply {
    name = "reproText"
    preferredSize = Dimension(800, 200)
  }

  private val actualText = PlaceholderTextArea(ACTUAL_TEXT).apply {
    name = "actualText"
    preferredSize = Dimension(800, 200)
  }

  private val expectedText = PlaceholderTextArea(EXPECTED_TEXT).apply {
    name = "expectedText"
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

  private val userText = JBTextField().apply {
    name = "userText"
    text = user
    isEnabled = false
  }

  private val grid = JPanel(GridBagLayout())

  public val issueTitle: String
    get() = titleText.text

  public val reproSteps: String
    get() = reproText.text
  public val actual: String
    get() = actualText.text
  public val expected: String
    get() = expectedText.text
  public val component: String
    get() = feedbackComponents.text

  init {
    title = "Create New Bug"
    isResizable = false
    isModal = true
    listModel.addElement(file.fileName.toString())

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

      add(expectedLabel, constraints)

      val actualLabel = JLabel().apply {
        text = "<html>Actual<br/>behavior:</html>"
      }

      constraints.gridy = 3

      add(actualLabel, constraints)

      val componentLabel = JLabel().apply {
        text = "Component:"
      }

      constraints.gridy = 4

      add(componentLabel, constraints)

      val filesLabel = JLabel().apply {
        text = "Attached Files:"
      }

      constraints.gridy = 5

      add(filesLabel, constraints)

      val userLabel = JLabel().apply {
        text = "Reporter:"
      }

      constraints.gridy = 7

      add(userLabel, constraints)

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

      add(expectedText, constraints)

      constraints.apply {
        gridy = 3
      }

      add(actualText, constraints)

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

      constraints.apply {
        gridx = 1
        gridy = 7
        gridwidth = 1
        gridheight = 1
      }

      add(userText, constraints)

      val addButton = JButton().apply {
        text = "Add"
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
      }

      constraints.apply {
        gridy = 6
      }

      add(removeButton, constraints)
    }

    init()
  }

  override fun createActions(): Array<Action> {
    val submit = object : DialogWrapperAction("Submit") {
      override fun doAction(e: ActionEvent) {
        close(OK_EXIT_CODE)
      }
    }

    val cancel = object : DialogWrapperAction("Cancel") {
      override fun doAction(e: ActionEvent) {
        close(NEXT_USER_EXIT_CODE)
      }
    }

    return arrayOf(submit, cancel)
  }

  override fun createCenterPanel(): JComponent = grid
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
