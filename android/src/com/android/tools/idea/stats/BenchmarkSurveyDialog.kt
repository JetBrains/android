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
package com.android.tools.idea.stats

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import java.awt.Font
import javax.swing.GroupLayout
import javax.swing.JComponent
import javax.swing.JPanel

private const val TITLE = "Take the Android Studio Survey"
private const val TEXT_1 = "Help us improve Android Studio by taking a survey."
private const val TEXT_2 = "You will be directed to a Qualtrics survey in your default browser."
private const val TEXT_3 = "You may complete the survey at your convenience."
private const val ASK_AGAIN_BUTTON_TEXT = "Ask again later"
private const val OPEN_SURVEY_BUTTON_TEXT = "Open survey"

class BenchmarkSurveyDialog()
  : DialogWrapper(null) {

  private val panel = JPanel().apply {
    preferredSize = JBDimension(100, 100)
  }

  init {
    isAutoAdjustable = true
    isResizable = false
    title = TITLE
    isOKActionEnabled = true
    isModal = true

    val label1 = JBLabel(TEXT_1).apply {
      font = Font(font.name, Font.BOLD, font.size)
      preferredSize = JBDimension(40, 40)
    }

    val label2 = JBLabel("$TEXT_2 $TEXT_3").apply {
      isAllowAutoWrapping = true
      preferredSize = JBDimension(40, 40)
    }

    val groupLayout = GroupLayout(panel).apply {
      autoCreateGaps = true
      autoCreateContainerGaps = true
    }

    val vGroup = groupLayout.createSequentialGroup()
      .addComponent(label1)
      .addComponent(label2)
    groupLayout.setVerticalGroup(vGroup)

    val hGroup = groupLayout.createParallelGroup()
      .addComponent(label1)
      .addComponent(label2)
    groupLayout.setHorizontalGroup(hGroup)

    panel.layout = groupLayout

    setOKButtonText(
      OPEN_SURVEY_BUTTON_TEXT
    )
    setCancelButtonText(
      ASK_AGAIN_BUTTON_TEXT
    )

    init()
  }

  override fun createCenterPanel(): JComponent = panel

  override fun doOKAction() {
    super.doOKAction()
  }
}
