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
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.milliseconds
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.styling.TooltipColors
import org.jetbrains.jewel.ui.component.styling.TooltipMetrics
import org.jetbrains.jewel.ui.component.styling.TooltipStyle
import org.jetbrains.jewel.ui.theme.tooltipStyle

// This whole file is mostly a fork of Jewel's Tooltip to add support for optional
// tooltips. The obvious approach is to just invoke the content without the Tooltip wrapper, but
// this works poorly with focus: if the error state changes, the node structure changes, and
// focus is lost.

/**
 * Displays a tooltip when hovering over the given [content] if [errorMessage] is not null;
 * otherwise, just displays the content.
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
  Tooltip(tooltip = errorMessage?.let { { Text(it) } }, modifier, style, tooltipPlacement, content)
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

/** A variant of org.jetbrains.jewel.ui.component.Tooltip that makes [tooltip] optional. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Tooltip(
  tooltip: (@Composable () -> Unit)?,
  modifier: Modifier = Modifier,
  style: TooltipStyle = JewelTheme.tooltipStyle,
  tooltipPlacement: TooltipPlacement = style.metrics.placement,
  content: @Composable () -> Unit,
) {
  TooltipArea(
    tooltip = {
      if (tooltip != null) {
        CompositionLocalProvider(LocalContentColor provides style.colors.content) {
          Box(
            modifier =
              Modifier.shadow(
                  elevation = style.metrics.shadowSize,
                  shape = RoundedCornerShape(style.metrics.cornerSize),
                  ambientColor = style.colors.shadow,
                  spotColor = Color.Transparent,
                )
                .background(
                  color = style.colors.background,
                  shape = RoundedCornerShape(style.metrics.cornerSize),
                )
                .border(
                  width = style.metrics.borderWidth,
                  color = style.colors.border,
                  shape = RoundedCornerShape(style.metrics.cornerSize),
                )
                .padding(style.metrics.contentPadding)
          ) {
            tooltip()
          }
        }
      }
    },
    modifier = modifier,
    tooltipPlacement = tooltipPlacement,
    content = content,
  )
}
