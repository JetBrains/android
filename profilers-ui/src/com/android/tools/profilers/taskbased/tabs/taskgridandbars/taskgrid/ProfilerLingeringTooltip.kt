/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.profilers.taskbased.tabs.taskgridandbars.taskgrid

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.areAnyPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.MouseEvent
import java.awt.event.WindowEvent
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.LocalComponent
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

/**
 * A variant of org.jetbrains.jewel.ui.component.Tooltip that lingers briefly after the mouse leaves the content node, and remains present
 * as long as the mouse is on the tooltip.
 *
 * NOTE: This is a fork of `com.android.tools.adtui.compose.LingeringTooltip` from commit 9858c7640fabca256832b28abaa4fda04db30467.
 *
 * Changes made in this fork:
 * 1. Removed boundsInWindow calculation and startHidingIfNotHovered which mixed window and popup coordinates causing the tooltip to stick.
 * 2. Refactored event handling to avoid passing lambdas as keys to pointerInput, which was causing the underlying coroutine to cancel and
 *    restart on every mouse movement, degrading performance and dropping events.
 * 3. Combined Enter and Move pointer event handling logic to fix an edge case where quick exit/re-entry could result in the tooltip failing
 *    to cancel the hiding job.
 * 4. Added a global AWTEventListener that strictly checks mouse events against the exact LocalComponent.current bounds using
 *    SwingUtilities.isDescendingFrom. This guarantees that if the mouse teleports (e.g. via an OS swipe gesture) to another Compose or
 *    Swing UI element, the tooltip hides perfectly.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfilerLingeringTooltip(
  tooltip: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  delayMillis: Int = 500,
  lingerMillis: Int = 500,
  style: TooltipStyle = JewelTheme.tooltipStyle,
  tooltipPlacement: TooltipPlacement = style.metrics.placement,
  content: @Composable () -> Unit,
) {
  ProfilerLingeringTooltipArea(
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
                .background(color = style.colors.background, shape = RoundedCornerShape(style.metrics.cornerSize))
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
 * Adjusts the link style in tooltips; since tooltips sometimes have a dark background like notifications, we use the theme color for links
 * in notifications.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun tooltipLinkStyle(): LinkStyle {
  val baseStyle = JewelTheme.linkStyle
  val linkColor = JBColor.namedColor("Notification.linkForeground", JBUI.CurrentTheme.Link.Foreground.ENABLED).toComposeColor()
  return LinkStyle(
    LinkColors(linkColor, linkColor, linkColor, linkColor, linkColor, linkColor),
    baseStyle.metrics,
    baseStyle.icons,
    baseStyle.underlineBehavior,
  )
}

/** Sets the tooltip for an element. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalJewelApi::class)
@Composable
fun ProfilerLingeringTooltipArea(
  tooltip: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  delayMillis: Int = 500,
  lingerMillis: Int = 500,
  tooltipPlacement: TooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 16.dp)),
  content: @Composable () -> Unit,
) {
  var cursorPosition by remember { mutableStateOf(Offset.Zero) }
  var isVisible by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  // If true, job was launched by startShowing; otherwise it was launched by startHiding
  var jobIsShowing by remember { mutableStateOf(false) }
  var job: Job? by remember { mutableStateOf(null) }

  fun startChangingVisibility(show: Boolean) {
    if (jobIsShowing == show && job?.isActive == true) { // Don't restart the job if it's already active
      return
    }
    job?.cancel()
    jobIsShowing = show
    job = scope.launch {
      delay((if (show) delayMillis else lingerMillis).milliseconds)
      isVisible = show
    }
  }

  fun hide() {
    job?.cancel()
    job = null
    isVisible = false
  }

  fun startHiding() {
    startChangingVisibility(show = false)
  }

  val hostComponent =
    if (ApplicationManager.getApplication()?.isUnitTestMode == true) {
      null
    } else {
      LocalComponent.current
    }
  var popupComponent by remember { mutableStateOf<java.awt.Component?>(null) }

  Box(
    modifier =
      modifier
        .pointerInput(Unit) {
          awaitPointerEventScope {
            while (true) {
              val event = awaitPointerEvent(PointerEventPass.Main)
              when (event.type) {
                PointerEventType.Enter,
                PointerEventType.Move -> {
                  cursorPosition = event.position
                  if (!jobIsShowing && !event.buttons.areAnyPressed) {
                    startChangingVisibility(show = true)
                  }
                }
                PointerEventType.Exit -> startHiding()
              }
            }
          }
        }
        .pointerInput(Unit) {
          awaitPointerEventScope {
            while (true) {
              val event = awaitPointerEvent(PointerEventPass.Initial)
              if (event.type == PointerEventType.Press) {
                hide()
              }
            }
          }
        }
  ) {
    content()
    if (isVisible) {
      DisposableEffect(hostComponent) {
        if (hostComponent == null) return@DisposableEffect onDispose {}

        val awtListener = AWTEventListener { event ->
          if (event is MouseEvent) {
            val source = event.source as? java.awt.Component
            if (
              source != null &&
                !SwingUtilities.isDescendingFrom(source, hostComponent) &&
                (popupComponent == null || !SwingUtilities.isDescendingFrom(source, popupComponent))
            ) {
              hide()
            }
          } else if (event is WindowEvent && event.id == WindowEvent.WINDOW_LOST_FOCUS) {
            hide()
          }
        }
        Toolkit.getDefaultToolkit()
          .addAWTEventListener(awtListener, AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK or AWTEvent.WINDOW_EVENT_MASK)

        onDispose { Toolkit.getDefaultToolkit().removeAWTEventListener(awtListener) }
      }
      @OptIn(ExperimentalFoundationApi::class)
      Popup(popupPositionProvider = tooltipPlacement.positionProvider(cursorPosition), onDismissRequest = { isVisible = false }) {
        val currentPopupComponent =
          if (ApplicationManager.getApplication()?.isUnitTestMode == true) {
            null
          } else {
            LocalComponent.current
          }
        DisposableEffect(currentPopupComponent) {
          popupComponent = currentPopupComponent
          onDispose { popupComponent = null }
        }
        Box(
          Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
              while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                when (event.type) {
                  PointerEventType.Enter,
                  PointerEventType.Move -> {
                    event.changes.forEach { e -> e.consume() }
                    if (!jobIsShowing) {
                      job?.cancel()
                      job = null
                      jobIsShowing = true
                    }
                  }
                  PointerEventType.Exit -> startHiding()
                }
              }
            }
          }
        ) {
          tooltip()
        }
      }
    }
  }
}

private val PointerEvent.position
  get() = changes.first().position
