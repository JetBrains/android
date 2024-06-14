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
import com.android.tools.idea.insights.IssueDetails
import com.android.tools.idea.insights.IssueVariant
import com.intellij.openapi.ui.getUserData
import com.intellij.openapi.ui.putUserData
import com.intellij.openapi.util.Key
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.util.preferredWidth
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.ItemEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.border.CompoundBorder
import org.jetbrains.annotations.VisibleForTesting

@VisibleForTesting val KEY = Key.create<Pair<String, String>>("android.aqi.details.header")

data class DetailsPanelHeaderModel(
  val icon: Icon?,
  val className: String,
  val methodName: String,
  val eventCount: Long,
  val userCount: Long,
) {
  companion object {
    fun fromIssueVariant(issue: IssueDetails, variant: IssueVariant?): DetailsPanelHeaderModel {
      val (className, methodName) = issue.getDisplayTitle()
      return variant?.let {
        DetailsPanelHeaderModel(
          issue.fatality.getIcon(),
          className,
          methodName,
          it.eventsCount,
          it.impactedDevicesCount,
        )
      }
        ?: DetailsPanelHeaderModel(
          issue.fatality.getIcon(),
          className,
          methodName,
          issue.eventsCount,
          issue.impactedDevicesCount,
        )
    }
  }
}

class DetailsPanelHeader(
  private val variantComboBox: VariantComboBox? = null,
  onVariantSelected: (IssueVariant?) -> Unit = {},
) : JPanel(BorderLayout()) {
  @VisibleForTesting val titleLabel = JBLabel()

  // User, event counts
  @VisibleForTesting val eventsCountLabel = JLabel(StudioIcons.AppQualityInsights.ISSUE)
  @VisibleForTesting
  val usersCountLabel = JLabel(StudioIcons.LayoutEditor.Palette.QUICK_CONTACT_BADGE)
  private val countsPanel =
    transparentPanel().apply {
      layout = BoxLayout(this, BoxLayout.X_AXIS)
      add(eventsCountLabel, Box.CENTER_ALIGNMENT)
      add(Box.createHorizontalStrut(8))
      add(usersCountLabel, Box.CENTER_ALIGNMENT)
      border = JBUI.Borders.emptyRight(8)
    }

  private val titleVariantSeparatorPanel =
    JPanel(BorderLayout()).apply {
      add(
        JSeparator(JSeparator.VERTICAL).apply {
          foreground = JBUI.CurrentTheme.Toolbar.SEPARATOR_COLOR
        }
      )
      border = JBUI.Borders.empty(5, 2)
    }

  @VisibleForTesting val variantPanel: JPanel

  init {
    if (variantComboBox != null) {
      variantComboBox.addItemListener { itemEvent ->
        if (itemEvent.stateChange == ItemEvent.SELECTED) {
          (itemEvent.item as? VariantRow)?.let { onVariantSelected(it.issueVariant) }
        }
      }

      variantComboBox.model.addListener {
        val (className, methodName) = titleLabel.getUserData(KEY) ?: return@addListener
        titleLabel.text = generateTitleLabelText(className, methodName)
      }
      variantPanel =
        transparentPanel(BorderLayout()).apply {
          isVisible = false
          add(variantComboBox, BorderLayout.CENTER)
        }
      titleVariantSeparatorPanel.isVisible = true
    } else {
      variantPanel = transparentPanel().apply { isVisible = false }
      titleVariantSeparatorPanel.isVisible = false
    }

    val contentPanel =
      transparentPanel().apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        add(titleLabel)
        add(Box.createHorizontalStrut(5))
        add(titleVariantSeparatorPanel)
        add(variantPanel)
      }

    add(contentPanel, BorderLayout.WEST)
    add(countsPanel, BorderLayout.EAST)
    border =
      CompoundBorder(JBUI.Borders.customLineBottom(JBColor.border()), JBUI.Borders.emptyLeft(8))

    addComponentListener(
      object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent) {
          val (className, methodName) = titleLabel.getUserData(KEY) ?: return
          titleLabel.text = generateTitleLabelText(className, methodName)
        }
      }
    )
  }

  override fun updateUI() {
    super.updateUI()
    preferredSize = Dimension(0, JBUI.scale(35))
  }

  fun clear() {
    titleLabel.icon = null
    titleLabel.text = null
    countsPanel.isVisible = false
    variantPanel.isVisible = false
  }

  fun update(model: DetailsPanelHeaderModel) {
    titleLabel.icon = model.icon
    countsPanel.isVisible = true
    eventsCountLabel.text = model.eventCount.formatNumberToPrettyString()
    usersCountLabel.text = model.userCount.formatNumberToPrettyString()
    if (variantComboBox != null) {
      variantPanel.isVisible = true
    }
    titleLabel.putUserData(KEY, Pair(model.className, model.methodName))
    titleLabel.text = generateTitleLabelText(model.className, model.methodName)
  }

  private fun generateTitleLabelText(className: String, methodName: String): String {
    val contentWidth = width - countsPanel.width
    val remainingWidth = contentWidth - 5 - variantPanel.preferredWidth - 20
    return generateTitleLabelText(className, methodName, remainingWidth, titleLabel.font)
  }

  @VisibleForTesting
  fun generateTitleLabelText(
    className: String,
    methodName: String,
    contentWidth: Int,
    displayFont: Font,
  ): String {
    var remainingWidth = contentWidth
    if (remainingWidth <= 0) return "<html></html>"
    val shrunkenMethodText =
      if (methodName.isNotEmpty()) {
        val methodFontMetrics = getFontMetrics(displayFont.deriveFont(Font.BOLD))
        AdtUiUtils.shrinkToFit(
            methodName,
            methodFontMetrics,
            remainingWidth.toFloat(),
            AdtUiUtils.ShrinkDirection.TRUNCATE_START,
          )
          .also { remainingWidth -= methodFontMetrics.stringWidth(it) }
      } else {
        ""
      }

    val shrunkenClassText =
      if (remainingWidth > 0) {
        val classFontMetrics = getFontMetrics(displayFont)
        AdtUiUtils.shrinkToFit(
            "$className.",
            classFontMetrics,
            remainingWidth.toFloat(),
            AdtUiUtils.ShrinkDirection.TRUNCATE_START,
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
