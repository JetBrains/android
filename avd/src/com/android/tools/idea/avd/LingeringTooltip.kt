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
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.foundation.theme.OverrideDarkMode
import org.jetbrains.jewel.ui.component.styling.LinkColors
import org.jetbrains.jewel.ui.component.styling.LinkStyle
import org.jetbrains.jewel.ui.component.styling.LocalLinkStyle
import org.jetbrains.jewel.ui.component.styling.TooltipStyle
import org.jetbrains.jewel.ui.theme.linkStyle
import org.jetbrains.jewel.ui.theme.tooltipStyle
import org.jetbrains.jewel.ui.util.isDark

// This whole file is a fork of Compose's TooltipArea to add support for tooltips that don't
// immediately disappear when the mouse leaves the target element. This is particularly useful for
// tooltips that contain clickable URLs, so that the user can move their mouse to the link before it
// goes away.

/**
 * A variant of org.jetbrains.jewel.ui.component.Tooltip that lingers briefly after the mouse leaves
 * the content node, and remains present as long as the mouse is on the tooltip.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LingeringTooltip(
  tooltip: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  delayMillis: Int = 500,
  lingerMillis: Int = 500,
  style: TooltipStyle = JewelTheme.tooltipStyle,
  tooltipPlacement: TooltipPlacement = style.metrics.placement,
  content: @Composable () -> Unit,
) {
  LingeringTooltipArea(
    tooltip = {
      if (enabled) {
        CompositionLocalProvider(
          LocalContentColor provides style.colors.content,
          LocalTextStyle provides LocalTextStyle.current.copy(color = style.colors.content),
          LocalLinkStyle provides tooltipLinkStyle(),
        ) {
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
            OverrideDarkMode(style.colors.background.isDark()) { tooltip() }
          }
        }
      }
    },
    modifier = modifier,
    delayMillis = delayMillis,
    lingerMillis = lingerMillis,
    tooltipPlacement = tooltipPlacement,
    content = content,
  )
}

/**
 * Adjusts the link style in tooltips; since tooltips sometimes have a dark background like
 * notifications, we use the theme color for links in notifications.
 */
@Composable
private fun tooltipLinkStyle(): LinkStyle {
  val baseStyle = JewelTheme.linkStyle
  val linkColor =
    JBColor.namedColor("Notification.linkForeground", JBUI.CurrentTheme.Link.Foreground.ENABLED)
      .toComposeColor()
  return LinkStyle(
    LinkColors(linkColor, linkColor, linkColor, linkColor, linkColor, linkColor),
    baseStyle.metrics,
    baseStyle.icons,
    baseStyle.underlineBehavior,
  )
}

/**
 * Sets the tooltip for an element.
 *
 * @param tooltip Composable content of the tooltip.
 * @param modifier The modifier to be applied to the layout.
 * @param delayMillis Delay in milliseconds before the tooltip is shown.
 * @param lingerMillis Delay in milliseconds before the tooltip is hidden.
 * @param tooltipPlacement Defines position of the tooltip.
 * @param content Composable content that the current tooltip is set to.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LingeringTooltipArea(
  tooltip: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  delayMillis: Int = 500,
  lingerMillis: Int = 500,
  tooltipPlacement: TooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 16.dp)),
  content: @Composable () -> Unit,
) {
  var parentBounds by remember { mutableStateOf(Rect.Zero) }
  var popupBounds by remember { mutableStateOf(Rect.Zero) }
  var cursorPosition by remember { mutableStateOf(Offset.Zero) }
  var isVisible by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  // If true, job was launched by startShowing; otherwise it was launched by startHiding
  var jobIsShowing by remember { mutableStateOf(false) }
  var job: Job? by remember { mutableStateOf(null) }

  fun startChangingVisibility(show: Boolean) {
    if (
      jobIsShowing == show && job?.isActive == true
    ) { // Don't restart the job if it's already active
      return
    }
    job?.cancel()
    jobIsShowing = show
    job =
      scope.launch {
        delay((if (show) delayMillis else lingerMillis).toLong())
        isVisible = show
      }
  }

  fun hide() {
    job?.cancel()
    job = null
    isVisible = false
  }

  fun startHidingIfNotHovered(globalPosition: Offset) {
    if (!parentBounds.contains(globalPosition) && !popupBounds.contains(globalPosition)) {
      startChangingVisibility(show = false)
    }
  }

  Box(
    modifier =
      modifier
        .onGloballyPositioned { parentBounds = it.boundsInWindow() }
        .onPointerEvent(PointerEventType.Enter) {
          cursorPosition = it.position
          if (!isVisible && !it.buttons.areAnyPressed) {
            startChangingVisibility(show = true)
          }
        }
        .onPointerEvent(PointerEventType.Move) {
          cursorPosition = it.position
          if (!isVisible && !it.buttons.areAnyPressed) {
            startChangingVisibility(show = true)
          }
        }
        .onPointerEvent(PointerEventType.Exit) {
          startHidingIfNotHovered(parentBounds.topLeft + it.position)
        }
        .onPointerEvent(PointerEventType.Press, pass = PointerEventPass.Initial) { hide() }
  ) {
    content()
    if (isVisible) {
      @OptIn(ExperimentalFoundationApi::class)
      (Popup(
        popupPositionProvider = tooltipPlacement.positionProvider(cursorPosition),
        onDismissRequest = { isVisible = false },
      ) {
        var popupPosition by remember { mutableStateOf(Offset.Zero) }
        Box(
          Modifier.onGloballyPositioned {
              popupBounds = it.boundsInWindow()
              popupPosition = it.positionInWindow()
            }
            .onPointerEvent(PointerEventType.Enter) {
              // Since the tooltip is visible, can assume the job is hiding the tooltip
              job?.cancel()
              job = null
            }
            .onPointerEvent(PointerEventType.Move) {
              startHidingIfNotHovered(popupPosition + it.position)
            }
            .onPointerEvent(PointerEventType.Exit) {
              startHidingIfNotHovered(popupPosition + it.position)
            }
        ) {
          tooltip()
        }
      })
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
