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
package com.android.tools.idea.common.surface

import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.idea.common.error.Issue
import com.android.tools.idea.common.error.IssueSource
import com.android.tools.idea.common.error.fixWithAiActionProvider
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.rendering.RenderProblem
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.Gray
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.JPanel
import javax.swing.border.LineBorder

private const val ERROR_LABEL_CONTENT = "Render problem"
private val TRANSLUCENT_BACKGROUND_COLOR = Gray._220.withAlpha(200)
private val DEFAULT_BORDER = LineBorder(AdtUiUtils.DEFAULT_BORDER_COLOR, 1)
private val DEFAULT_HEIGHT = 35
private val EXTENDED_HEIGHT = DEFAULT_HEIGHT * 2

/** Shows a Panel with an error message */
class SceneViewErrorsPanel(
  val errorProvider: () -> List<Throwable>?,
  val styleProvider: () -> Style = { Style.SOLID },
) : JPanel(BorderLayout()), UiDataProvider {

  /** The style applied to the panel */
  enum class Style {
    /** The panel is not visible */
    HIDDEN,

    /** The panel is showing and is opaque */
    SOLID,

    /** The panel is showing and is translucent */
    TRANSLUCENT,
  }

  private val height =
    if (StudioFlags.COMPOSE_RENDER_ERROR_FIX_WITH_AI.get()) EXTENDED_HEIGHT else DEFAULT_HEIGHT
  private val size = JBUI.size(130, height)
  private val label =
    JBLabel(ERROR_LABEL_CONTENT).apply {
      foreground = Gray._119
      minimumSize = size
      border = JBUI.Borders.empty(10)
    }
  private val boldFont = UIUtil.getLabelFont().deriveFont(Font.BOLD)
  private var lastStyle: Style? = null
  private val fixWithAiActionGroup = DefaultActionGroup()
  private val fixWithAiToolbar: ActionToolbar =
    ActionManager.getInstance()
      .createActionToolbar("SceneViewErrorsPanelToolbar", fixWithAiActionGroup, true)
      .apply {
        targetComponent = this@SceneViewErrorsPanel
        component.isOpaque = false
        component.border = JBUI.Borders.empty()
      }

  init {
    add(label, BorderLayout.CENTER)
    add(fixWithAiToolbar.component, BorderLayout.SOUTH)
    updateStyles()
  }

  override fun getPreferredSize() = size

  override fun getMinimumSize() = size

  private fun updateFixWithAiButton() {
    fixWithAiActionGroup.removeAll()
    if (getIssues().isNotEmpty()) {
      fixWithAiActionProvider()?.let { action ->
        action.templatePresentation.putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true)
        fixWithAiActionGroup.add(action)
      }
    }
    ApplicationManager.getApplication().invokeLater { fixWithAiToolbar.updateActionsAsync() }
  }

  /** Updates the look and feel of the panel with the style and returns the current set style. */
  private fun updateStyles(): Style {
    val newStyle = styleProvider()
    if (newStyle != lastStyle) {
      // We update the button every time the style changes.
      // When the panel is visible, we might add the "Fix with AI" button.
      // When it's hidden, the button will be removed.
      if (newStyle == Style.SOLID && StudioFlags.COMPOSE_RENDER_ERROR_FIX_WITH_AI.get()) {
        updateFixWithAiButton()
      } else {
        fixWithAiActionGroup.removeAll()
        ApplicationManager.getApplication().invokeLater { fixWithAiToolbar.updateActionsAsync() }
      }
      when (newStyle) {
        Style.SOLID -> {
          label.foreground = Gray._119
          border = DEFAULT_BORDER
          label.font = UIUtil.getLabelFont()
          background = UIUtil.getPanelBackground()
        }
        Style.TRANSLUCENT -> {
          label.foreground = Gray._15
          border = JBUI.Borders.empty()
          label.font = boldFont
          background = TRANSLUCENT_BACKGROUND_COLOR
        }
        Style.HIDDEN -> {}
      }
      lastStyle = newStyle
    }
    return newStyle
  }

  override fun uiDataSnapshot(sink: DataSink) {
    getIssues().firstOrNull()?.let { sink[PlatformDataKeys.SELECTED_ITEM] = it }
  }

  override fun isVisible(): Boolean {
    return updateStyles() != Style.HIDDEN
  }

  /** Returns a list of [Issue]s from the current errors. */
  private fun getIssues(): List<Issue> = errorProvider()?.map { toIssue(it) } ?: emptyList()

  /** Converts a [RenderProblem] to an [Issue]. */
  private fun toIssue(throwable: Throwable): Issue =
    object : Issue() {
      override val summary: String = throwable.message ?: throwable.message ?: "Render error"
      override val description: String = throwable.stackTraceToString()
      override val severity: HighlightSeverity = HighlightSeverity.ERROR
      override val source: IssueSource = IssueSource.NONE
      override val category: String = "Render Error"
      override val throwable: Throwable = throwable
    }
}
