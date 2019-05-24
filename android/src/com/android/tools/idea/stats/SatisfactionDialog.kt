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

import com.google.wireless.android.sdk.stats.UserSentiment
import com.google.wireless.android.sdk.stats.UserSentiment.SatisfactionLevel.DISSATISFIED
import com.google.wireless.android.sdk.stats.UserSentiment.SatisfactionLevel.NEUTRAL
import com.google.wireless.android.sdk.stats.UserSentiment.SatisfactionLevel.SATISFIED
import com.google.wireless.android.sdk.stats.UserSentiment.SatisfactionLevel.UNKNOWN_SATISFACTION_LEVEL
import com.google.wireless.android.sdk.stats.UserSentiment.SatisfactionLevel.VERY_DISSATISFIED
import com.google.wireless.android.sdk.stats.UserSentiment.SatisfactionLevel.VERY_SATISFIED
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.DialogUtil
import com.intellij.util.ui.JBUI
import icons.StudioIcons.Shell.Telemetry.SENTIMENT_DISSATISFIED
import icons.StudioIcons.Shell.Telemetry.SENTIMENT_NEUTRAL
import icons.StudioIcons.Shell.Telemetry.SENTIMENT_SATISFIED
import icons.StudioIcons.Shell.Telemetry.SENTIMENT_VERY_DISSATISFIED
import icons.StudioIcons.Shell.Telemetry.SENTIMENT_VERY_SATISFIED
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.ItemEvent
import java.awt.event.ItemListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.lang.RuntimeException
import javax.swing.Box
import javax.swing.ButtonGroup
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JRadioButton


private const val DIALOG_TITLE = "Android Studio Feedback"
private const val USER_PROMPT_TEXT = "Overall, how satisfied are you with this product?"
private const val OK_BUTTON_TEXT = "Submit"
private val CENTER_PANEL_BORDER = JBUI.Borders.empty(0, 0, 10, 50)
private val PROMPT_CONTENT_SPACING = JBUI.scale(10)

private data class SentimentInput(val icon: Icon,
                                  val level: UserSentiment.SatisfactionLevel,
                                  val label: String)

private val actions = listOf(
  SentimentInput(SENTIMENT_VERY_SATISFIED, VERY_SATISFIED, "Very satisfied"),
  SentimentInput(SENTIMENT_SATISFIED, SATISFIED, "Somewhat satisfied"),
  SentimentInput(SENTIMENT_NEUTRAL, NEUTRAL, "Neither satisfied or dissatisfied"),
  SentimentInput(SENTIMENT_DISSATISFIED, DISSATISFIED, "Somewhat dissatisfied"),
  SentimentInput(SENTIMENT_VERY_DISSATISFIED, VERY_DISSATISFIED, "Very dissatisfied")
)

/**
 * Dialog prompting users for their level of satisfaction.
 * The [UserSentiment.SatisfactionLevel] selected by the user is accessible using [selectedSentiment].
 */
class SatisfactionDialog : DialogWrapper(null), ActionListener, ItemListener {
  var selectedSentiment = UNKNOWN_SATISFACTION_LEVEL

  private val buttonGroup = ButtonGroup()

  val content: JComponent = Box.createVerticalBox().apply {
    border = CENTER_PANEL_BORDER
    add(JBLabel(USER_PROMPT_TEXT))
    add(Box.createVerticalStrut(PROMPT_CONTENT_SPACING))
    actions.map(::createButton)
      .forEach { it -> add(it) }
  }

  init {
    isAutoAdjustable = true
    setOKButtonText(OK_BUTTON_TEXT)
    setResizable(false)
    title = DIALOG_TITLE
    isOKActionEnabled = false
    isModal = false
    init()
  }

  override fun createCenterPanel(): JComponent = content

  override fun getPreferredFocusedComponent(): JComponent? = buttonGroup.elements.nextElement()

  override fun createCancelAction() = ActionListener { cancel() }

  private fun cancel() {
    selectedSentiment = UNKNOWN_SATISFACTION_LEVEL
  }

  override fun doCancelAction() {
    cancel()
    super.doCancelAction()
  }

  /**
   * Implementation of [ItemListener] to handle keyboard selection.
   */
  override fun itemStateChanged(e: ItemEvent) {
    val button = e.item as? JRadioButton ?: return
    if (button.isSelected) {
      handleSelection(button.actionCommand)
    }
  }

  override fun actionPerformed(e: ActionEvent) {
    val actionCommand = e.actionCommand
    handleSelection(actionCommand)
  }

  private fun handleSelection(actionCommand: String) {
    selectedSentiment = try {
      UserSentiment.SatisfactionLevel.valueOf(actionCommand)
    }
    catch (e: Exception) {
      Logger.getInstance(SatisfactionDialog::class.java).error(e)
      UNKNOWN_SATISFACTION_LEVEL
    }
    isOKActionEnabled = selectedSentiment != UNKNOWN_SATISFACTION_LEVEL
  }

  private fun createButton(sentiment: SentimentInput) = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
    alignmentX = JPanel.LEFT_ALIGNMENT
    val button = JRadioButton().apply {
      actionCommand = sentiment.level.name
      addActionListener(this@SatisfactionDialog)
      addItemListener(this@SatisfactionDialog)
      buttonGroup.add(this)
    }
    add(button)
    add(JBLabel(sentiment.label, sentiment.icon, JBLabel.LEFT).apply {
      addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          delegateActionToButton(button, e)
        }
      })
    })
  }

  /**
   * Select the button when the user clicks the label
   */
  private fun delegateActionToButton(button: JRadioButton, e: MouseEvent) {
    button.requestFocus()
    button.isSelected = true
    button.actionListeners.forEach { actionListener ->
      actionListener.actionPerformed(ActionEvent(e.source, e.id, button.actionCommand))
    }
  }
}

class ShowSatisfactionDialogAction : DumbAwareAction("Show satisfaction dialog") {
  override fun actionPerformed(e: AnActionEvent) {
    if (ApplicationManager.getApplication().isInternal) {
      AndroidStudioUsageTracker.requestUserSentiment()
    }
    else {
      throw RuntimeException("${ShowSatisfactionDialogAction::class.simpleName} can only be called in internal builds")
    }
  }
}
