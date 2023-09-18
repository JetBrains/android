/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.util.ActionToolbarUtil
import com.android.tools.idea.insights.AppInsightsIssue
import com.android.tools.idea.insights.AppInsightsProjectLevelController
import com.android.tools.idea.insights.IssueVariant
import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.Selection
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actions.AbstractToggleUseSoftWrapsAction
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces
import com.intellij.openapi.ui.getUserData
import com.intellij.openapi.ui.putUserData
import com.intellij.openapi.util.Key
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.util.preferredWidth
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.border.CompoundBorder
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.annotations.VisibleForTesting

private val KEY = Key.create<Pair<String, String>>("android.aqi.details.header")

class DetailsPanelHeader(
  editor: Editor,
  private val controller: AppInsightsProjectLevelController,
  private val supportsVariants: Boolean
) : JPanel(BorderLayout()) {
  @VisibleForTesting val titleLabel = JBLabel()

  private val wrapAction =
    object : AbstractToggleUseSoftWrapsAction(SoftWrapAppliancePlaces.CONSOLE, false) {
      init {
        ActionUtil.copyFrom(this, IdeActions.ACTION_EDITOR_USE_SOFT_WRAPS)
      }
      override fun getEditor(e: AnActionEvent) = editor
      override fun getActionUpdateThread() = ActionUpdateThread.EDT
    }

  @VisibleForTesting
  val toolbar =
    ActionManager.getInstance()
      .createActionToolbar("StackTraceToolbar", DefaultActionGroup(wrapAction), true)

  @VisibleForTesting
  val comboBoxStateFlow = MutableStateFlow<VariantComboBoxState>(DisabledComboBoxState.empty)
  private val variantComboBox = VariantComboBox(controller.coroutineScope, comboBoxStateFlow)
  @VisibleForTesting
  val variantPanel =
    transparentPanel(BorderLayout()).apply {
      isVisible = false
      add(JBLabel("|").apply { border = JBUI.Borders.empty(0, 5) }, BorderLayout.WEST)
      add(variantComboBox, BorderLayout.CENTER)
    }
  private val contentPanel =
    transparentPanel().apply {
      layout = BoxLayout(this, BoxLayout.X_AXIS)
      add(titleLabel)
      add(Box.createHorizontalStrut(5))
      add(variantPanel)
    }

  init {
    border = JBUI.Borders.empty()
    add(contentPanel, BorderLayout.WEST)
    toolbar.targetComponent = this
    toolbar.layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY
    toolbar.setReservePlaceAutoPopupIcon(false)
    ActionToolbarUtil.makeToolbarNavigable(toolbar)
    toolbar.component.apply {
      isOpaque = false
      isVisible = false
    }
    add(toolbar.component, BorderLayout.EAST)
    border =
      CompoundBorder(JBUI.Borders.customLineBottom(JBColor.border()), JBUI.Borders.emptyLeft(8))
    preferredSize = Dimension(0, JBUIScale.scale(28))
    variantComboBox.renderer = variantComboBoxListCellRenderer
    variantComboBox.addItemListener { itemEvent ->
      (itemEvent.item as? VariantRow)?.let { controller.selectIssueVariant(it.issueVariant) }
    }
    addComponentListener(
      object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent) {
          val (className, methodName) = titleLabel.getUserData(KEY) ?: return
          titleLabel.text = generateTitleLabelText(className, methodName)
        }
      }
    )
  }

  fun clear() {
    titleLabel.icon = null
    titleLabel.text = null
    toolbar.component.isVisible = false
    variantPanel.isVisible = false
  }

  fun updateWithIssue(issue: AppInsightsIssue) {
    titleLabel.icon = issue.issueDetails.fatality.getIcon()
    val (className, methodName) = issue.issueDetails.getDisplayTitle()
    toolbar.component.isVisible = true
    if (supportsVariants) {
      comboBoxStateFlow.value = DisabledComboBoxState.loading
      variantPanel.isVisible = true
      titleLabel.putUserData(KEY, Pair(className, methodName))
      titleLabel.text = generateTitleLabelText(className, methodName)
    } else {
      val methodString =
        if (methodName.isNotEmpty()) {
          ".<B>$methodName</B>"
        } else ""
      titleLabel.text = "<html>$className$methodString</html>"
    }
  }

  fun updateComboBox(
    issue: AppInsightsIssue,
    variants: LoadingState.Done<Selection<IssueVariant>?>
  ) {
    require(supportsVariants)
    when (variants) {
      is LoadingState.Ready -> {
        comboBoxStateFlow.value =
          if (variants.value?.items.isNullOrEmpty()) DisabledComboBoxState.empty
          else PopulatedComboBoxState(issue, variants.value!!)
      }
      is LoadingState.Failure -> {
        comboBoxStateFlow.value = DisabledComboBoxState.failure
      }
    }
  }

  @VisibleForTesting
  fun generateTitleLabelText(className: String, methodName: String): String {
    val contentWidth = width - toolbar.component.width
    var remainingWidth = contentWidth - 5 - variantPanel.preferredWidth - 20
    if (remainingWidth <= 0) return "<html></html>"
    val shrunkenMethodText =
      if (methodName.isNotEmpty()) {
        val methodFontMetrics = getFontMetrics(titleLabel.font.deriveFont(Font.BOLD))
        AdtUiUtils.shrinkToFit(
            methodName,
            methodFontMetrics,
            remainingWidth.toFloat(),
            AdtUiUtils.ShrinkDirection.TRUNCATE_START
          )
          .also { remainingWidth -= methodFontMetrics.stringWidth(it) }
      } else {
        ""
      }

    val shrunkenClassText =
      if (remainingWidth > 0) {
        val classFontMetrics = getFontMetrics(titleLabel.font)
        AdtUiUtils.shrinkToFit(
            "$className.",
            classFontMetrics,
            remainingWidth.toFloat(),
            AdtUiUtils.ShrinkDirection.TRUNCATE_START
          )
          .also { remainingWidth -= classFontMetrics.stringWidth(it) }
      } else ""

    val methodString =
      if (shrunkenMethodText.isNotEmpty()) {
        "<B>$shrunkenMethodText</B>"
      } else ""
    return "<html>$shrunkenClassText$methodString</html>"
  }
}
