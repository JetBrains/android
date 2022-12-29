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
package com.google.services.firebase.insights.ui

import com.android.tools.adtui.common.ColoredIconGenerator.generateColoredIcon
import com.android.tools.idea.insights.ui.getDisplayTitle
import com.android.tools.idea.insights.ui.ifZero
import com.google.services.firebase.insights.CrashlyticsInsight
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.PopupChooserBuilder
import com.intellij.ui.CollectionListModel
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import org.jetbrains.annotations.VisibleForTesting

class AppInsightsGutterIconAction(
  private val project: Project,
  private val insights: List<CrashlyticsInsight>
) : AnAction() {
  private val logger: Logger
    get() = Logger.getInstance(javaClass)

  override fun actionPerformed(e: AnActionEvent) {
    if (insights.isEmpty()) return

    val popup =
      PopupChooserBuilder(JBList(CollectionListModel(insights)))
        .apply {
          setTitle("App Quality Insights")
          setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
          setMovable(true)
          setRenderer(AppInsightsGutterListCellRenderer())
          setItemChosenCallback { chosenInsight ->
            logger.debug("Gutter icon click for issue $chosenInsight")
            AppInsightsToolWindowFactory.show(project) { chosenInsight.markAsSelected() }
          }

          // Create the bottom panel.
          val panel = JPanel(BorderLayout())
          panel.border = JBUI.Borders.emptyLeft(5)
          val hintText =
            ResizedSimpleColoredComponent().apply {
              append("Select an issue to see details", SimpleTextAttributes.REGULAR_ATTRIBUTES)
              foreground = UIUtil.getLabelDisabledForeground()
            }
          panel.add(hintText, BorderLayout.WEST)

          val eventsTotal = insights.sumOf { it.issue.issueDetails.eventsCount }
          val usersTotal = insights.sumOf { it.issue.issueDetails.impactedDevicesCount }
          val eventsComponent =
            ResizedSimpleColoredComponent().apply {
              icon =
                generateColoredIcon(
                  StudioIcons.AppQualityInsights.ISSUE,
                  UIUtil.getLabelDisabledForeground()
                )
              append(eventsTotal.ifZero("-"), SimpleTextAttributes.REGULAR_ATTRIBUTES)
            }

          val usersComponent =
            ResizedSimpleColoredComponent().apply {
              icon =
                generateColoredIcon(
                  StudioIcons.LayoutEditor.Palette.QUICK_CONTACT_BADGE,
                  UIUtil.getLabelDisabledForeground()
                )
              append(usersTotal.ifZero("-"), SimpleTextAttributes.REGULAR_ATTRIBUTES)
            }

          val countsPanel =
            JPanel().apply {
              isOpaque = false
              add(eventsComponent)
              add(usersComponent)
            }
          panel.add(countsPanel, BorderLayout.EAST)
          setSouthComponent(panel)
        }
        .createPopup()

    (e.inputEvent as? MouseEvent)?.let { mouseEvent -> popup.show(RelativePoint(mouseEvent)) }
      ?: run {
        // Gutter actions are always triggered by mouse, so this shouldn't ever be reached.
        popup.showInBestPositionFor(e.dataContext)
      }
  }
}

private class AppInsightsGutterListCellRenderer : ListCellRenderer<CrashlyticsInsight> {
  override fun getListCellRendererComponent(
    list: JList<out CrashlyticsInsight>,
    value: CrashlyticsInsight,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean
  ): Component {
    val renderer = JPanel(BorderLayout())
    renderer.border = JBUI.Borders.emptyLeft(5)

    val (className, methodName) = value.issue.issueDetails.getDisplayTitle()
    val leftComponent =
      JListSimpleColoredComponent(value.issue.issueDetails.fatality.getIcon(), list, cellHasFocus)
        .apply {
          toolTipText = value.issue.issueDetails.subtitle
          append(className, SimpleTextAttributes.REGULAR_ATTRIBUTES)
          if (methodName.isNotEmpty()) {
            append(".", SimpleTextAttributes.REGULAR_ATTRIBUTES)
            append(methodName, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
          }
        }
    renderer.add(leftComponent, BorderLayout.WEST)

    val eventsComponent =
      JListSimpleColoredComponent(StudioIcons.AppQualityInsights.ISSUE, list, cellHasFocus).apply {
        append(
          value.issue.issueDetails.eventsCount.ifZero("-"),
          SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
        )
      }

    val usersComponent =
      JListSimpleColoredComponent(
          StudioIcons.LayoutEditor.Palette.QUICK_CONTACT_BADGE,
          list,
          cellHasFocus
        )
        .apply {
          append(
            value.issue.issueDetails.impactedDevicesCount.ifZero("-"),
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
    renderer.foreground = if (cellHasFocus) list.selectionForeground else list.foreground
    renderer.background = if (cellHasFocus) list.selectionBackground else list.background
    return renderer
  }
}

@VisibleForTesting
open class ResizedSimpleColoredComponent : SimpleColoredComponent() {
  init {
    isOpaque = false
    isTransparentIconBackground = true
    font = UIUtil.getListFont()
  }

  override fun getPreferredSize(): Dimension {
    return UIUtil.updateListRowHeight(super.getPreferredSize())
  }
}

@VisibleForTesting
class JListSimpleColoredComponent<T>(icon: Icon?, list: JList<T>, hasFocus: Boolean) :
  ResizedSimpleColoredComponent() {
  init {
    font = list.font
    foreground =
      if (hasFocus) {
        list.selectionForeground
      } else {
        list.foreground
      }
    if (icon != null) {
      this.icon = if (hasFocus) generateColoredIcon(icon, foreground) else icon
    }
  }
}
