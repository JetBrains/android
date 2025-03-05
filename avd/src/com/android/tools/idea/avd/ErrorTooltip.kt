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
package com.android.tools.idea.avd

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.milliseconds
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.styling.TooltipColors
import org.jetbrains.jewel.ui.component.styling.TooltipMetrics
import org.jetbrains.jewel.ui.component.styling.TooltipStyle
import org.jetbrains.jewel.ui.theme.tooltipStyle

/**
 * Displays a tooltip with error styling when hovering over the given [content] if [errorMessage] is
 * not null; otherwise, just displays the content.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ErrorTooltip(
  errorMessage: String?,
  modifier: Modifier = Modifier,
  style: TooltipStyle = validationErrorTooltipStyle(),
  tooltipPlacement: TooltipPlacement = style.metrics.placement,
  content: @Composable () -> Unit,
) {
  if (errorMessage != null) {
    Tooltip(
      tooltip = { Text(errorMessage) },
      modifier = modifier,
      style = style,
      tooltipPlacement = tooltipPlacement,
      content = content,
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun validationErrorTooltipStyle(): TooltipStyle {
  val colors = JewelTheme.tooltipStyle.colors
  val metrics = JewelTheme.tooltipStyle.metrics
  return TooltipStyle(
    TooltipColors(
      background = JewelTheme.globalColors.outlines.error,
      content = JewelTheme.globalColors.text.normal,
      border = JewelTheme.globalColors.outlines.focusedError,
      shadow = colors.shadow,
    ),
    TooltipMetrics(
      contentPadding = metrics.contentPadding,
      showDelay = 500.milliseconds,
      cornerSize = metrics.cornerSize,
      borderWidth = metrics.borderWidth,
      shadowSize = metrics.shadowSize,
      placement =
        TooltipPlacement.ComponentRect(
          anchor = Alignment.TopCenter,
          alignment = Alignment.TopEnd,
          offset = DpOffset(0.dp, -8.dp),
        ),
    ),
  )
}
