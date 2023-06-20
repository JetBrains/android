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
package com.android.tools.idea.insights.ui

import com.android.tools.adtui.common.ColoredIconGenerator
import com.android.tools.idea.insights.AppInsightsIssue
import com.android.tools.idea.insights.FailureType
import com.android.tools.idea.insights.IssueDetails
import com.android.tools.idea.insights.ui.AppInsightsIssuesTableView.Companion.LOGGER
import com.intellij.icons.AllIcons
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import java.awt.Color
import java.awt.LayoutManager
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import org.jetbrains.annotations.VisibleForTesting

fun transparentPanel() = JPanel().apply { isOpaque = false }

fun transparentPanel(layout: LayoutManager) = JPanel(layout).apply { isOpaque = false }

class AppInsightsStatusText(owner: JComponent?, private val checkStatusVisible: () -> Boolean) :
  StatusText(owner) {
  @VisibleForTesting public override fun isStatusVisible() = checkStatusVisible()
}

val offlineModeIcon =
  ColoredIconGenerator.generateColoredIcon(
    AllIcons.Actions.OfflineMode,
    UIUtil.getErrorForeground()
  )

fun Any?.ifZero(fallback: String) = this.toString().takeUnless { it == "0" } ?: fallback

val dateFormatter: DateTimeFormatter
  get() =
    DateTimeFormatter.ofPattern("MMM d, yyyy, hh:mm:ss a")
      .withLocale(Locale.getDefault())
      .withZone(ZoneId.systemDefault())

val EMPTY_STATE_TITLE_FORMAT = SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES
val EMPTY_STATE_TEXT_FORMAT =
  SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER, NamedColorUtil.getInactiveTextColor())
val EMPTY_STATE_LINK_FORMAT =
  SimpleTextAttributes(
    SimpleTextAttributes.STYLE_SMALLER or SimpleTextAttributes.LINK_ATTRIBUTES.style,
    SimpleTextAttributes.LINK_ATTRIBUTES.fgColor
  )

fun IssueDetails.getDisplayTitle(): Pair<String, String> {
  val splitSubtitle = subtitle.split('.')
  if (splitSubtitle.size < 2) {
    LOGGER.warn("Failed to format subtitle: $subtitle")
    return title to ""
  }
  val (className, methodName) = splitSubtitle.takeLast(2)
  return className to methodName
}

// This should return a plain-text version of what's shown in the cell.
internal fun convertToSearchText(issue: AppInsightsIssue): String {
  val (className, methodName) = issue.issueDetails.getDisplayTitle()
  return if (methodName.isEmpty()) {
    className
  } else {
    "$className.$methodName"
  }
}

fun getFatalityIcon(
  fatality: FailureType,
  selected: Boolean,
  foreground: Color,
  withNote: Boolean = false
): Icon? {
  val icon =
    if (withNote) {
      when (fatality) {
        FailureType.FATAL -> StudioIcons.AppQualityInsights.FATAL_WITH_NOTE
        FailureType.NON_FATAL -> StudioIcons.AppQualityInsights.NON_FATAL_WITH_NOTE
        FailureType.ANR -> StudioIcons.AppQualityInsights.ANR_WITH_NOTE
        // This scenario shouldn't ever be reached.
        else -> null
      }
    } else {
      fatality.getIcon()
    }
  return if (icon == null) {
    null
  } else if (selected) {
    ColoredIconGenerator.generateColoredIcon(icon, foreground)
  } else {
    icon
  }
}
