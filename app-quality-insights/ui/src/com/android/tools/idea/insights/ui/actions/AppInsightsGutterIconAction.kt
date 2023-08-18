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
package com.android.tools.idea.insights.ui.actions

import com.android.tools.adtui.common.ColoredIconGenerator.generateColoredIcon
import com.android.tools.adtui.ui.DynamicRendererList
import com.android.tools.idea.insights.AppInsight
import com.android.tools.idea.insights.ui.JListSimpleColoredComponent
import com.android.tools.idea.insights.ui.ResizedSimpleColoredComponent
import com.android.tools.idea.insights.ui.formatNumberToPrettyString
import com.android.tools.idea.insights.ui.getDisplayTitle
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.PopupChooserBuilder
import com.intellij.ui.CollectionListModel
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.event.MouseEvent
import javax.swing.DefaultListSelectionModel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import org.jetbrains.annotations.VisibleForTesting

class AppInsightsGutterIconAction(
  private val insights: List<AppInsight>,
  private val itemChosenCallback: (AppInsight) -> Unit
) : AnAction() {
  private val logger: Logger
    get() = Logger.getInstance(javaClass)

  override fun actionPerformed(e: AnActionEvent) {
    if (insights.isEmpty()) return

    val renderItems = generateRenderInstructions(insights)
    val list = createGroupedJList(renderItems)

    lateinit var popup: JBPopup
    popup =
      PopupChooserBuilder(list)
        .apply {
          setTitle("App Quality Insights")
          setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
          setMovable(true)
          setRenderer(AppInsightsGutterListCellRenderer())
          setItemChosenCallback { chosenInsight ->
            if (chosenInsight is InsightInstruction) {
              logger.debug("Gutter icon click for issue $chosenInsight")
              popup.closeOk(null)
              itemChosenCallback(chosenInsight.insight)
            }
          }
          setCloseOnEnter(false)

          // Create the bottom panel.
          val panel = JPanel(BorderLayout())
          panel.border = JBUI.Borders.emptyLeft(5)
          val hintText =
            ResizedSimpleColoredComponent().apply {
              append("Select an issue to see details", SimpleTextAttributes.REGULAR_ATTRIBUTES)
              foreground = UIUtil.getLabelDisabledForeground()
            }
          panel.add(hintText, BorderLayout.WEST)

          if (insights.groupBy { it.provider }.size == 1) {
            val eventsTotal = insights.sumOf { it.issue.issueDetails.eventsCount }
            val usersTotal = insights.sumOf { it.issue.issueDetails.impactedDevicesCount }
            val eventsComponent =
              ResizedSimpleColoredComponent().apply {
                icon =
                  generateColoredIcon(
                    StudioIcons.AppQualityInsights.ISSUE,
                    UIUtil.getLabelDisabledForeground()
                  )
                append(
                  eventsTotal.formatNumberToPrettyString(),
                  SimpleTextAttributes.REGULAR_ATTRIBUTES
                )
              }

            val usersComponent =
              ResizedSimpleColoredComponent().apply {
                icon =
                  generateColoredIcon(
                    StudioIcons.LayoutEditor.Palette.QUICK_CONTACT_BADGE,
                    UIUtil.getLabelDisabledForeground()
                  )
                append(
                  usersTotal.formatNumberToPrettyString(),
                  SimpleTextAttributes.REGULAR_ATTRIBUTES
                )
              }

            val countsPanel =
              JPanel().apply {
                isOpaque = false
                add(eventsComponent)
                add(usersComponent)
              }
            panel.add(countsPanel, BorderLayout.EAST)
          }
          setSouthComponent(panel)
        }
        .createPopup()

    (e.inputEvent as? MouseEvent)?.let { mouseEvent -> popup.show(RelativePoint(mouseEvent)) }
      ?: run {
        // Gutter actions are always triggered by mouse, so this shouldn't ever be reached.
        popup.showInBestPositionFor(e.dataContext)
      }
  }

  private fun createGroupedJList(
    renderInstructions: List<RenderInstruction>
  ): JList<RenderInstruction> {
    val variableHeightJList =
      DynamicRendererList.createDynamicRendererList<RenderInstruction>(
        CollectionListModel(renderInstructions)
      )

    // The PopupChooserBuilder we use overrides the cursor to HAND_CURSOR by default.
    // So we need to update the cursor depending on whether the selection is on
    // a selectable row.
    variableHeightJList.selectionModel =
      object : DefaultListSelectionModel() {
        override fun setSelectionInterval(index0: Int, index1: Int) {
          if (index1 == -1) return
          val item = renderInstructions[index1]
          if (item is InsightInstruction) {
            variableHeightJList.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
          } else {
            variableHeightJList.cursor = Cursor.getDefaultCursor()
          }
          super.setSelectionInterval(index0, index1)
        }
      }
    return variableHeightJList
  }

  private fun generateRenderInstructions(insights: List<AppInsight>) =
    insights
      .groupBy { it.provider }
      .toSortedMap()
      .mapValues { (provider, insights) ->
        // Map each insight to a RenderItem and insert a HeaderItem at the head of the list.
        listOf(HeaderInstruction(provider.displayName)) +
          insights.map { insight -> InsightInstruction(insight) }
      }
      .toList()
      .fold(emptyList<RenderInstruction>()) { acc, (provider, insightsByProvider) ->
        // Insert a divider item when there are two or more categories.
        if (acc.isEmpty()) {
          insightsByProvider
        } else {
          acc + listOf(SeparatorInstruction) + insightsByProvider
        }
      }
}

@VisibleForTesting
/** Instruction for rendering a row in the JList. */
sealed class RenderInstruction

@VisibleForTesting
/** A plain text row representing the header of a group. */
data class HeaderInstruction(val name: String) : RenderInstruction()

/** A horizontal separator. */
@VisibleForTesting object SeparatorInstruction : RenderInstruction()

@VisibleForTesting
/** A row for an app insight. */
data class InsightInstruction(val insight: AppInsight) : RenderInstruction()

private class AppInsightsGutterListCellRenderer : ListCellRenderer<RenderInstruction> {
  override fun getListCellRendererComponent(
    list: JList<out RenderInstruction>,
    value: RenderInstruction,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean
  ): Component {
    return when (value) {
      is HeaderInstruction -> {
        val renderer = JPanel(BorderLayout())
        renderer.border = JBUI.Borders.emptyLeft(10)
        renderer.add(
          ResizedSimpleColoredComponent().apply {
            append(value.name, SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
          },
          BorderLayout.WEST
        )
        renderer
      }
      is SeparatorInstruction -> JSeparator()
      is InsightInstruction -> {
        val renderer = JPanel(BorderLayout())
        val hasFocus = list.selectedValue == value
        renderer.border = JBUI.Borders.emptyLeft(5)

        val issueDetails = value.insight.issue.issueDetails
        val (className, methodName) = issueDetails.getDisplayTitle()
        val leftComponent =
          JListSimpleColoredComponent(issueDetails.fatality.getIcon(), list, hasFocus).apply {
            toolTipText = issueDetails.subtitle
            append(className, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            if (methodName.isNotEmpty()) {
              append(".", SimpleTextAttributes.REGULAR_ATTRIBUTES)
              append(methodName, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
            }
          }
        renderer.add(leftComponent, BorderLayout.WEST)

        val eventsComponent =
          JListSimpleColoredComponent(StudioIcons.AppQualityInsights.ISSUE, list, hasFocus).apply {
            append(
              issueDetails.eventsCount.formatNumberToPrettyString(),
              SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
            )
          }

        val usersComponent =
          JListSimpleColoredComponent(
              StudioIcons.LayoutEditor.Palette.QUICK_CONTACT_BADGE,
              list,
              hasFocus
            )
            .apply {
              append(
                issueDetails.impactedDevicesCount.formatNumberToPrettyString(),
                SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
              )
            }

        val countsPanel =
          JPanel().apply {
            isOpaque = false
            border = JBUI.Borders.emptyLeft(15)
            add(eventsComponent)
            add(usersComponent)
          }
        renderer.add(countsPanel, BorderLayout.EAST)
        renderer.foreground = if (hasFocus) list.selectionForeground else list.foreground
        renderer.background = if (hasFocus) list.selectionBackground else list.background
        renderer
      }
    }
  }
}
