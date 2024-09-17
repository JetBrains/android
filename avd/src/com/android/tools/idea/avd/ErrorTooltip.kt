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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.areAnyPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.styling.TooltipColors
import org.jetbrains.jewel.ui.component.styling.TooltipMetrics
import org.jetbrains.jewel.ui.component.styling.TooltipStyle
import org.jetbrains.jewel.ui.theme.tooltipStyle

// This whole file is mostly a fork of Compose/Jewel's Tooltip to add support for optional
// tooltips. The obvious approach is to just invoke the content without the Tooltip wrapper, but
// this works poorly with focus: if the error state changes, the node structure changes, and
// focus is lost. There's no way to do a "no-op" tooltip with TooltipArea; you always get an empty
// box of some sort on hover.

/**
 * Displays a tooltip when hovering over the given [content] if [errorMessage] is not null;
 * otherwise, just displays the content.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ErrorTooltip(
  errorMessage: String?,
  style: TooltipStyle = validationErrorTooltipStyle(),
  tooltipPlacement: TooltipPlacement = style.metrics.placement,
  content: @Composable () -> Unit,
) {
  ErrorTooltipArea(
    tooltip = {
      if (errorMessage != null) {
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
            Text(errorMessage)
          }
        }
      }
    },
    tooltipPlacement = tooltipPlacement,
    content = content,
  )
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

/**
 * Sets the tooltip for an element.
 *
 * @param tooltip Composable content of the tooltip.
 * @param modifier The modifier to be applied to the layout.
 * @param delayMillis Delay in milliseconds.
 * @param tooltipPlacement Defines position of the tooltip.
 * @param content Composable content that the current tooltip is set to.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ErrorTooltipArea(
  tooltip: (@Composable () -> Unit)?,
  modifier: Modifier = Modifier,
  delayMillis: Int = 500,
  tooltipPlacement: TooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 16.dp)),
  content: @Composable () -> Unit,
) {
  var parentBounds by remember { mutableStateOf(Rect.Zero) }
  var cursorPosition by remember { mutableStateOf(Offset.Zero) }
  var isVisible by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  var job: Job? by remember { mutableStateOf(null) }

  fun startShowing() {
    if (job?.isActive == true) { // Don't restart the job if it's already active
      return
    }
    job =
      scope.launch {
        delay(delayMillis.toLong())
        isVisible = true
      }
  }

  fun hide() {
    job?.cancel()
    job = null
    isVisible = false
  }

  fun hideIfNotHovered(globalPosition: Offset) {
    if (!parentBounds.contains(globalPosition)) {
      hide()
    }
  }

  Box(
    modifier =
      modifier
        .onGloballyPositioned { parentBounds = it.boundsInWindow() }
        .onPointerEvent(PointerEventType.Enter) {
          cursorPosition = it.position
          if (!isVisible && !it.buttons.areAnyPressed) {
            startShowing()
          }
        }
        .onPointerEvent(PointerEventType.Move) {
          cursorPosition = it.position
          if (!isVisible && !it.buttons.areAnyPressed) {
            startShowing()
          }
        }
        .onPointerEvent(PointerEventType.Exit) {
          hideIfNotHovered(parentBounds.topLeft + it.position)
        }
        .onPointerEvent(PointerEventType.Press, pass = PointerEventPass.Initial) { hide() }
  ) {
    content()

    if (tooltip == null) {
      hide()
    } else if (isVisible) {
      @OptIn(ExperimentalFoundationApi::class)
      (Popup(
        popupPositionProvider = tooltipPlacement.positionProvider(cursorPosition),
        onDismissRequest = { isVisible = false },
      ) {
        var popupPosition by remember { mutableStateOf(Offset.Zero) }
        Box(
          Modifier.onGloballyPositioned { popupPosition = it.positionInWindow() }
            .onPointerEvent(PointerEventType.Move) { hideIfNotHovered(popupPosition + it.position) }
            .onPointerEvent(PointerEventType.Exit) { hideIfNotHovered(popupPosition + it.position) }
        ) {
          tooltip()
        }
      })
    } else {
      startShowing()
    }
  }
}

private val PointerEvent.position
  get() = changes.first().position

private fun Modifier.onPointerEvent(
  eventType: PointerEventType,
  pass: PointerEventPass = PointerEventPass.Main,
  onEvent: AwaitPointerEventScope.(event: PointerEvent) -> Unit,
) =
  pointerInput(eventType, pass, onEvent) {
    awaitPointerEventScope {
      while (true) {
        val event = awaitPointerEvent(pass)
        if (event.type == eventType) {
          onEvent(event)
        }
      }
    }
  }
