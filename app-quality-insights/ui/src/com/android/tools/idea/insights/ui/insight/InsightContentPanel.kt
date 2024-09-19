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
package com.android.tools.idea.insights.ui.insight

import com.android.tools.idea.insights.AiInsight
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.ui.AppInsightsStatusText
import com.android.tools.idea.insights.ui.EMPTY_STATE_TEXT_FORMAT
import com.android.tools.idea.insights.ui.EMPTY_STATE_TITLE_FORMAT
import com.android.tools.idea.insights.ui.InsightPermissionDeniedHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.Project
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

private const val RESOURCE_EXHAUSTED_MESSAGE =
  "Quota exceeded for quota metric 'Duet Task API requests' and limit 'Duet Task API requests per day per user'"

/** [JPanel] that is shown in the [InsightToolWindow] when an insight is available. */
class InsightContentPanel(
  project: Project,
  scope: CoroutineScope,
  currentInsightFlow: Flow<LoadingState<AiInsight?>>,
  parentDisposable: Disposable,
  permissionDeniedHandler: InsightPermissionDeniedHandler,
  enableInsightHandler: () -> Unit,
  onRefresh: (Boolean) -> Unit,
) : JPanel(), DataProvider, Disposable {

  private val cardLayout = CardLayout()

  private val insightTextPane = InsightTextPane()
  private val feedbackPanel = InsightFeedbackPanel()
  private val insightBottomPanel = InsightBottomPanel(project) { onRefresh(it) }

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
          addActionListener { enableInsightHandler() }
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
      setLoadingText("Generating insight...")
      border = JBUI.Borders.empty()
      add(insightScrollPanel, BorderLayout.CENTER)
      add(insightBottomPanel, BorderLayout.SOUTH)
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
            // Gemini plugin disabled or scope is not authorized
            is LoadingState.Unauthorized -> {
              emptyStateText.apply {
                clear()
                appendText("Gemini is disabled", EMPTY_STATE_TITLE_FORMAT)
                appendLine(
                  "To see insights, please enable and authorize the Gemini plugin",
                  EMPTY_STATE_TEXT_FORMAT,
                  null,
                )
              }
              showEmptyCard()
            }
            // Permission denied message is confusing. Provide a generic message
            is LoadingState.PermissionDenied -> {
              permissionDeniedHandler.handlePermissionDenied(aiInsight, emptyStateText)
              showEmptyCard()
            }
            is LoadingState.TosNotAccepted -> {
              showToSCard()
            }
            is LoadingState.UnsupportedOperation -> {
              emptyStateText.apply {
                clear()
                val cause = aiInsight.message ?: ""
                appendText("No insight available", EMPTY_STATE_TITLE_FORMAT)
                appendLine(cause, EMPTY_STATE_TEXT_FORMAT, null)
              }
              showEmptyCard()
            }
            is LoadingState.NetworkFailure -> {
              val message = aiInsight.message
              if (message?.contains(RESOURCE_EXHAUSTED_MESSAGE) == true) {
                emptyStateText.apply {
                  clear()
                  appendText("Quota exhausted", EMPTY_STATE_TITLE_FORMAT)
                  appendLine("You have consumed your available daily quota for insights.")
                }
              } else {
                emptyStateText.apply {
                  clear()
                  appendText("Insights data is not available.")
                }
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
    togglePanelVisibilities(!startLoading)
    cardLayout.show(this, card)
  }

  private fun togglePanelVisibilities(visibility: Boolean) {
    insightPanel.isVisible = visibility
    insightBottomPanel.isVisible = visibility
  }

  override fun dispose() = Unit

  override fun getData(dataId: String) =
    when {
      PlatformDataKeys.COPY_PROVIDER.`is`(dataId) -> insightTextPane
      else -> null
    }
}
