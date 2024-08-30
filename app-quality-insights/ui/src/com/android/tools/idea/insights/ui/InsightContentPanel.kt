/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.insights.ui

import com.android.tools.idea.insights.AiInsight
import com.android.tools.idea.insights.LoadingState
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import icons.StudioIcons.StudioBot
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Graphics
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JButton
import javax.swing.JPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jdesktop.swingx.VerticalLayout
import org.jetbrains.annotations.VisibleForTesting

private const val CONTENT_CARD = "content"
private const val EMPTY_CARD = "empty"
private const val TOS_NOT_ACCEPTED = "tos_not_accepted"

/** [JPanel] that is shown in the [InsightToolWindow] when an insight is available. */
class InsightContentPanel(
  scope: CoroutineScope,
  currentInsightFlow: Flow<LoadingState<AiInsight?>>,
  parentDisposable: Disposable,
) : JPanel(), Disposable {

  private val cardLayout = CardLayout()

  private val insightTextPane = InsightTextPane()
  private val feedbackPanel = InsightFeedbackPanel()

  private val insightPanel =
    JPanel(VerticalLayout()).apply {
      add(insightTextPane)
      add(feedbackPanel)
    }

  private val insightScrollPanel =
    JPanel(BorderLayout()).apply {
      val scrollPane =
        ScrollPaneFactory.createScrollPane(
            insightPanel,
            JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER,
          )
          .apply {
            isOpaque = false
            border = JBUI.Borders.empty()
            background = JBColor.background()
          }

      add(scrollPane, BorderLayout.CENTER)
    }

  private val enableInsightPanel =
    JPanel(GridBagLayout()).apply {
      val enableInsightEmptyText =
        AppInsightsStatusText(this) { true }
          .apply {
            appendText("Insights require Gemini", EMPTY_STATE_TITLE_FORMAT)
            appendLine(
              "You can setup Gemini and enable insights via button below",
              EMPTY_STATE_TEXT_FORMAT,
              null,
            )
          }

      val button =
        JButton("Enable Insights", StudioBot.LOGO).apply {
          addActionListener {
            // TODO(b/361127260): Show ToS dialog
          }
          isFocusable = false
        }

      val gbc =
        GridBagConstraints().apply {
          gridx = 0
          gridy = 0
        }

      add(enableInsightEmptyText.component, gbc)

      gbc.apply { gridy = 1 }
      add(enableInsightEmptyText.secondaryComponent, gbc)

      gbc.apply { gridy = 3 }
      add(button, gbc)
    }

  private val loadingPanel =
    JBLoadingPanel(BorderLayout(), this).apply {
      border = JBUI.Borders.empty()
      add(insightScrollPanel)
    }

  private val emptyOrErrorPanel: JPanel =
    object : JPanel() {
      override fun paint(g: Graphics) {
        super.paint(g)
        emptyStateText.paint(this, g)
      }
    }

  private var isEmptyStateTextVisible = false
  @VisibleForTesting
  val emptyStateText = AppInsightsStatusText(emptyOrErrorPanel) { isEmptyStateTextVisible }

  init {
    Disposer.register(parentDisposable, this)
    layout = cardLayout
    border = JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0)

    add(loadingPanel, CONTENT_CARD)
    add(emptyOrErrorPanel, EMPTY_CARD)
    add(enableInsightPanel, TOS_NOT_ACCEPTED)

    scope.launch {
      currentInsightFlow
        .distinctUntilChanged()
        .onEach { reset() }
        .collect { aiInsight ->
          when (aiInsight) {
            is LoadingState.Ready -> {
              when (aiInsight.value) {
                null -> {
                  emptyStateText.apply {
                    clear()
                    appendText(
                      "Transient state (\"fetch insight\" action is not fired yet), should recover shortly."
                    )
                  }
                  showEmptyCard()
                }
                else -> {
                  val insight = aiInsight.value!!
                  if (insight.rawInsight.isEmpty()) {
                    emptyStateText.apply {
                      clear()
                      appendText("No insights", EMPTY_STATE_TITLE_FORMAT)
                      appendLine(
                        "There are no insights available for this issue",
                        EMPTY_STATE_TEXT_FORMAT,
                        null,
                      )
                    }
                    showEmptyCard()
                  } else {
                    insightTextPane.text = insight.rawInsight
                    showContentCard()
                  }
                }
              }
            }
            is LoadingState.Loading -> {
              insightTextPane.text = ""
              showContentCard(true)
            }
            // Permission denied message is confusing. Provide a generic message
            is LoadingState.PermissionDenied -> {
              emptyStateText.apply {
                clear()
                appendText("Request failed", EMPTY_STATE_TITLE_FORMAT)
                appendLine(
                  "You do not have permission to fetch insights",
                  EMPTY_STATE_TEXT_FORMAT,
                  null,
                )
              }
              showEmptyCard()
            }
            is LoadingState.ToSNotAccepted -> {
              showToSCard()
            }
            is LoadingState.NetworkFailure -> {
              emptyStateText.apply {
                clear()
                appendText("Insights data is not available.")
              }
              showEmptyCard()
            }
            is LoadingState.Failure -> {
              val cause =
                aiInsight.cause?.message ?: aiInsight.message ?: "An unknown failure occurred"
              emptyStateText.apply {
                clear()
                appendText("Request failed", EMPTY_STATE_TITLE_FORMAT)
                appendLine(cause, EMPTY_STATE_TEXT_FORMAT, null)
              }
              showEmptyCard()
            }
          }
        }
    }
  }

  private fun reset() {
    feedbackPanel.resetFeedback()
    enableInsightPanel.isVisible = false
  }

  private fun showEmptyCard() = showCard(EMPTY_CARD, false).also { isEmptyStateTextVisible = true }

  private fun showContentCard(startLoading: Boolean = false) =
    showCard(CONTENT_CARD, startLoading).also { isEmptyStateTextVisible = false }

  private fun showToSCard() =
    showCard(TOS_NOT_ACCEPTED, false).also { isEmptyStateTextVisible = false }

  private fun showCard(card: String, startLoading: Boolean) {
    if (startLoading) {
      loadingPanel.startLoading()
    } else {
      loadingPanel.stopLoading()
    }
    insightPanel.isVisible = !startLoading
    cardLayout.show(this, card)
  }

  override fun dispose() = Unit
}
