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
package com.android.tools.idea.journeys.view

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.ui.component.VerticalScrollbar
import org.jetbrains.jewel.ui.component.scrollbarContentSafePadding
import org.jetbrains.jewel.ui.component.styling.ScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility.AlwaysVisible
import org.jetbrains.jewel.ui.component.styling.TextAreaStyle
import org.jetbrains.jewel.ui.theme.scrollbarStyle
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.hostOs

// This is adapted and simplified from Jewel's TextArea code, which does not respect the
// heightIn constraints and always takes up maxHeight because of a bug. Once that is
// sorted in Jewel, this will be removed.
@Composable
internal fun HeightAutoSizingTextArea(
  state: TextFieldState,
  style: TextAreaStyle,
  modifier: Modifier,
  placeholder: @Composable () -> Unit,
) {
  val textStyle = JewelTheme.defaultTextStyle
  val scrollState = rememberScrollState()
  val cursorBrush = remember(textStyle.color) { SolidColor(textStyle.color) }

  BasicTextField(
    state = state,
    modifier = modifier,
    textStyle = textStyle,
    cursorBrush = cursorBrush,
    scrollState = scrollState,
    decorator =
      TextFieldDecorator { innerTextField ->
        val (contentPadding, innerEndPadding) =
          calculatePaddings(
            JewelTheme.scrollbarStyle,
            style,
            scrollState,
            LocalLayoutDirection.current,
          )

        TextAreaDecorationBox(
          innerTextField = {
            TextAreaScrollableContainer(
              scrollState = scrollState,
              style = JewelTheme.scrollbarStyle,
              modifier = Modifier.padding(style.metrics.borderWidth).padding(end = innerEndPadding),
            ) {
              Box(Modifier.padding(contentPadding)) { innerTextField() }
            }
          },
          textStyle = textStyle,
          modifier = modifier,
          placeholder = if (state.text.isEmpty()) placeholder else null,
          placeholderTextColor = style.colors.placeholder,
          placeholderModifier = Modifier.padding(contentPadding).padding(style.metrics.borderWidth),
        )
      },
  )
}

@Composable
private fun calculatePaddings(
  scrollbarStyle: ScrollbarStyle?,
  style: TextAreaStyle,
  scrollState: ScrollState,
  layoutDirection: LayoutDirection,
): Pair<PaddingValues, Dp> =
  if (scrollbarStyle != null) {
    with(style.metrics.contentPadding) {
      val paddingValues =
        PaddingValues(
          start = calculateStartPadding(layoutDirection),
          top = calculateTopPadding(),
          end = 0.dp,
          bottom = calculateBottomPadding(),
        )

      val scrollbarExtraPadding =
        if (scrollState.canScrollForward || scrollState.canScrollBackward) {
          scrollbarContentSafePadding(scrollbarStyle)
        } else 0.dp

      paddingValues to calculateEndPadding(layoutDirection) + scrollbarExtraPadding
    }
  } else {
    style.metrics.contentPadding to 0.dp
  }

@Composable
private fun TextAreaScrollableContainer(
  scrollState: ScrollState,
  style: ScrollbarStyle,
  modifier: Modifier,
  content: @Composable (() -> Unit),
) {
  var keepVisible by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()

  ScrollableContainerImpl(
    verticalScrollbar = {
      VerticalScrollbar(
        scrollState,
        style = style,
        modifier = Modifier.pointerHoverIcon(PointerIcon.Default).padding(1.dp),
        keepVisible = keepVisible,
      )
    },
    modifier =
      Modifier.withKeepVisible(style.scrollbarVisibility.lingerDuration, scope) {
        keepVisible = it
      },
    scrollbarStyle = style,
  ) {
    Box(modifier.layoutId("ID_CONTENT")) { content() }
  }
}

@Composable
private fun ScrollableContainerImpl(
  verticalScrollbar: (@Composable () -> Unit)?,
  modifier: Modifier,
  scrollbarStyle: ScrollbarStyle,
  content: @Composable () -> Unit,
) {
  Layout(
    content = {
      content()

      if (verticalScrollbar != null) {
        Box(Modifier.layoutId("ID_VERTICAL_SCROLLBAR")) { verticalScrollbar() }
      }
    },
    modifier,
  ) { measurables, incomingConstraints ->
    val verticalScrollbarMeasurable =
      measurables.find { it.layoutId == "ID_VERTICAL_SCROLLBAR" }
        ?: error("The vertical scrollbar is missing")

    val verticalScrollbarConstraints = Constraints.fixedHeight(incomingConstraints.maxHeight)
    val verticalScrollbarPlaceable =
      verticalScrollbarMeasurable.measure(verticalScrollbarConstraints)

    val isMacOs = hostOs == OS.MacOS
    val contentMeasurable =
      measurables.find { it.layoutId == "ID_CONTENT" } ?: error("Content not provided")

    val isAlwaysVisible = scrollbarStyle.scrollbarVisibility is AlwaysVisible
    val vScrollbarWidth =
      if (!isMacOs || isAlwaysVisible) {
        // The scrollbar on Windows/Linux, and on macOS when AlwaysVisible, needs
        // to be accounted for by subtracting its width from the available width)
        verticalScrollbarPlaceable.width
      } else {
        0
      }

    val contentWidth = incomingConstraints.maxWidth - vScrollbarWidth
    val contentConstraints =
      Constraints(
        minWidth = contentWidth,
        maxWidth = contentWidth,
        minHeight = incomingConstraints.minHeight,
        maxHeight = incomingConstraints.maxHeight,
      )
    val contentPlaceable = contentMeasurable.measure(contentConstraints)
    val width = contentPlaceable.width + vScrollbarWidth

    val height = contentPlaceable.height

    layout(width, height) {
      contentPlaceable.placeRelative(x = 0, y = 0, zIndex = 0f)
      verticalScrollbarPlaceable.placeRelative(
        x = width - verticalScrollbarPlaceable.width,
        y = 0,
        zIndex = 1f,
      )
    }
  }
}

private fun Modifier.withKeepVisible(
  lingerDuration: Duration,
  scope: CoroutineScope,
  onKeepVisibleChange: (Boolean) -> Unit,
) =
  pointerInput(scope) {
    var delayJob: Job? = null
    awaitEachGesture {
      val event = awaitPointerEvent()
      if (event.type == PointerEventType.Move) {
        delayJob?.cancel()
        onKeepVisibleChange(true)
        delayJob =
          scope.launch {
            delay(lingerDuration)
            onKeepVisibleChange(false)
          }
      }
    }
  }

@Composable
private fun TextAreaDecorationBox(
  innerTextField: @Composable () -> Unit,
  textStyle: TextStyle,
  modifier: Modifier,
  placeholder: (@Composable () -> Unit)?,
  placeholderTextColor: Color,
  placeholderModifier: Modifier,
) {
  Layout(
    content = {
      if (placeholder != null) {
        Box(
          modifier = placeholderModifier.layoutId(PLACEHOLDER_ID),
          contentAlignment = Alignment.TopStart,
        ) {
          CompositionLocalProvider(
            LocalTextStyle provides textStyle.copy(color = placeholderTextColor),
            LocalContentColor provides placeholderTextColor,
            content = placeholder,
          )
        }
      }

      Box(
        modifier = Modifier.layoutId(TEXT_AREA_ID),
        contentAlignment = Alignment.TopStart,
        propagateMinConstraints = true,
      ) {
        innerTextField()
      }
    },
    modifier,
  ) { measurables, incomingConstraints ->
    val textAreaPlaceable =
      measurables.single { it.layoutId == TEXT_AREA_ID }.measure(incomingConstraints)

    // Measure placeholder — it's allowed to be as wide as it wants, but the height matches the
    // textArea's
    val placeholderConstraints =
      Constraints(
        minWidth = 0,
        minHeight = textAreaPlaceable.height,
        maxHeight = textAreaPlaceable.height,
      )
    val placeholderPlaceable =
      measurables.find { it.layoutId == PLACEHOLDER_ID }?.measure(placeholderConstraints)

    layout(textAreaPlaceable.width, textAreaPlaceable.height) {
      // Placed similar to the input text below
      placeholderPlaceable?.placeRelative(0, 0)

      // Placed top-start
      textAreaPlaceable.placeRelative(0, 0)
    }
  }
}

private const val PLACEHOLDER_ID = "Placeholder"
private const val TEXT_AREA_ID = "TextField"
