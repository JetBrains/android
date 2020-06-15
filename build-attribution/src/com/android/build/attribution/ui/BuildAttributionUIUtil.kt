/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.build.attribution.ui

import com.android.build.attribution.ui.data.CriticalPathPluginUiData
import com.android.build.attribution.ui.data.IssueLevel
import com.android.build.attribution.ui.data.TaskIssueType
import com.android.build.attribution.ui.data.TaskUiData
import com.android.build.attribution.ui.data.TimeWithPercentage
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon


fun TimeWithPercentage.durationString() = durationString(timeMs)

fun TimeWithPercentage.percentageString() = "%.1f%%".format(percentage)

fun durationString(timeMs: Long) = "%.3f s".format(timeMs.toDouble() / 1000)

fun issuesCountString(warningsCount: Int, infoCount: Int) = when {
  warningsCount > 0 && infoCount > 0 -> "${warningsCountString(warningsCount)}, ${infoCountString(infoCount)}"
  warningsCount == 0 && infoCount > 0 -> infoCountString(infoCount)
  warningsCount > 0 && infoCount == 0 -> warningsCountString(warningsCount)
  else -> ""
}

fun warningsCountString(warningsCount: Int) = "${warningsCount} ${StringUtil.pluralize("warning", warningsCount)}"

fun infoCountString(infoCount: Int) = "${infoCount} info"

fun colorIcon(color: Color): Icon = JBUI.scale(ColorIcon(12, color))

fun warningIcon(): Icon = AllIcons.General.BalloonWarning

fun infoIcon(): Icon = AllIcons.General.BalloonInformation

fun emptyIcon(): Icon = EmptyIcon.ICON_16

fun mergedIcon(left: Icon, right: Icon): Icon = MergedIcon(left, JBUI.scale(6), right)

fun issueIcon(issueType: TaskIssueType): Icon = when (issueType.level) {
  IssueLevel.WARNING -> warningIcon()
  IssueLevel.INFO -> infoIcon()
}

fun taskIcon(taskData: TaskUiData): Icon = when {
  taskData.hasWarning -> warningIcon()
  taskData.hasInfo -> infoIcon()
  else -> emptyIcon()
}

fun pluginIcon(pluginData: CriticalPathPluginUiData): Icon = when {
  pluginData.warningCount > 0 -> warningIcon()
  pluginData.infoCount > 0 -> infoIcon()
  else -> emptyIcon()
}

private class MergedIcon internal constructor(
  private val leftIcon: Icon,
  private val horizontalStrut: Int,
  private val rightIcon: Icon
) : Icon {

  override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
    paintIconAlignedCenter(c, g, x, y, leftIcon)
    paintIconAlignedCenter(c, g, x + leftIcon.iconWidth + horizontalStrut, y, rightIcon)
  }

  private fun paintIconAlignedCenter(c: Component, g: Graphics, x: Int, y: Int, icon: Icon) {
    val iconHeight = iconHeight
    icon.paintIcon(c, g, x, y + (iconHeight - icon.iconHeight) / 2)
  }

  override fun getIconWidth(): Int {
    return leftIcon.iconWidth + horizontalStrut + rightIcon.iconWidth
  }

  override fun getIconHeight(): Int {
    return leftIcon.iconHeight.coerceAtLeast(rightIcon.iconHeight)
  }
}
