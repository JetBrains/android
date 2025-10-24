/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.adtui.compose.component

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import kotlin.math.min
import org.jetbrains.jewel.foundation.modifier.thenIf

/**
 * A container that can expand and collapse its content.
 *
 * When the content's height exceeds [maxCollapsedHeight], the container becomes "expandable", and
 * its state can be controlled by the [expanded] parameter. When not expanded, the container's
 * height is capped at [maxCollapsedHeight], and the content is clipped.
 *
 * A key behavior of this container is its focus management. When the content is overflowing (i.e.,
 * taller than [maxCollapsedHeight]) and the container is collapsed (`expanded` is false), the
 * entire content area becomes non-focusable. This prevents focus from being trapped on elements
 * that are clipped and not visible.
 *
 * Note that the [content] is _always_ laid out with as if there were no [maxCollapsedHeight], and
 * is only visually clipped. The `content` never "sees" that it's clipped to a max height when the
 * container is not [expanded].
 *
 * @param expanded True to show the full content, false to collapse it to [maxCollapsedHeight]. If
 *   the content is shorter than [maxCollapsedHeight] this has no effect.
 * @param onExpandableChange Callback invoked when the content's height changes from being smaller
 *   than [maxCollapsedHeight] to larger, or vice versa. The boolean parameter is `true` if the
 *   content is overflowing, and thus expandable.
 * @param modifier The [Modifier] to apply to this layout.
 * @param maxCollapsedHeight The maximum height of the container when it is not expanded.
 * @param animateHeightChange Whether to animate the height change when expanding or collapsing.
 * @param heightAnimationSpec The [AnimationSpec] to use for the height change animation.
 * @param content The composable content to display inside the container. Must contain one and only
 *   one top-level composable.
 */
@Composable
fun ExpandableContainer(
  expanded: Boolean,
  onExpandableChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  maxCollapsedHeight: Dp = 150.dp,
  animateHeightChange: Boolean = true,
  heightAnimationSpec: AnimationSpec<Int> = spring(),
  content: @Composable () -> Unit,
) {
  var actualHeightPx by remember { mutableIntStateOf(0) }
  var isOverflowing by remember { mutableStateOf(false) }

  val density = LocalDensity.current
  val actualHeightDp = with(density) { actualHeightPx.toDp() }
  val newIsOverflowing = actualHeightDp >= maxCollapsedHeight
  if (isOverflowing != newIsOverflowing) {
    onExpandableChange(newIsOverflowing)
    isOverflowing = newIsOverflowing
  }

  val maxCollapsedHeightPx = with(density) { maxCollapsedHeight.roundToPx() }
  var targetHeightPx by remember { mutableIntStateOf(0) }
  val animatedHeight by
    animateIntAsState(
      targetValue = targetHeightPx,
      animationSpec = heightAnimationSpec,
      label = "ExpandableContainer_height",
    )

  Layout(
    modifier =
      modifier
        .clipToBounds()
        .focusGroup()
        .thenIf(isOverflowing && !expanded) { focusProperties { canFocus = false } }
        .onSizeChanged { size -> actualHeightPx = size.height },
    content = content,
  ) { measurables, constraints ->
    val placeable =
      measurables.singleOrNull()?.measure(constraints)
        ?: error("ExpandableContainer must have a single child, but it had ${measurables.size}")

    val height = placeable.height.fastCoerceIn(constraints.minHeight, constraints.maxHeight)
    targetHeightPx = if (expanded) height else min(height, maxCollapsedHeightPx)

    layout(placeable.width, if (animateHeightChange) animatedHeight else targetHeightPx) {
      placeable.place(0, 0)
    }
  }
}