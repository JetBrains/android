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
package com.ibm.google.onboardingandauthentication.ui.modifier

import androidx.compose.foundation.ScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val VERTICAL_SCROLLBAR_CORNER_RADIUS = 4f
private const val VERTICAL_SCROLLBAR_END_PADDING = 0f

/**
 * Extension function to draw a vertical column scrollbar on a Composable.
 *
 * @param scrollState The ScrollState object representing the scroll state of the column.
 * @param width The width of the scrollbar.
 * @param showScrollBarTrack Whether to show the scrollbar track (optional).
 * @param scrollBarTrackColor The color of the scrollbar track.
 * @param scrollBarColor The color of the scrollbar.
 * @param scrollBarCornerRadius The corner radius of the scrollbar.
 * @param endPadding The end padding of the scrollbar.
 * @return A Modifier object with the vertical column scrollbar applied.
 */
@Composable
fun Modifier.verticalColumnScrollbar(
  scrollState: ScrollState,
  width: Dp = 4.dp,
  showScrollBarTrack: Boolean = true,
  scrollBarTrackColor: Color = MaterialTheme.colorScheme.outline,
  scrollBarColor: Color = MaterialTheme.colorScheme.outline,
  scrollBarCornerRadius: Float = VERTICAL_SCROLLBAR_CORNER_RADIUS,
  endPadding: Float = VERTICAL_SCROLLBAR_END_PADDING,
): Modifier {
  return drawWithContent {
    // Draw the column's content
    drawContent()
    // Dimensions and calculations
    val viewportHeight = this.size.height
    val totalContentHeight = scrollState.maxValue.toFloat() + viewportHeight
    val scrollValue = scrollState.value.toFloat()
    // Compute scrollbar height and position
    val scrollBarHeight = (viewportHeight / totalContentHeight) * viewportHeight
    val scrollBarStartOffset = (scrollValue / totalContentHeight) * viewportHeight
    // Draw the track (optional)
    if (showScrollBarTrack) {
      drawRoundRect(
        cornerRadius = CornerRadius(scrollBarCornerRadius),
        color = scrollBarTrackColor,
        topLeft = Offset(this.size.width - endPadding, VERTICAL_SCROLLBAR_END_PADDING),
        size = Size(width.toPx(), viewportHeight / 2),
      )
    }
    // Draw the scrollbar
    drawRoundRect(
      cornerRadius = CornerRadius(scrollBarCornerRadius),
      color = scrollBarColor,
      topLeft = Offset(this.size.width - endPadding, scrollBarStartOffset),
      size = Size(width.toPx(), scrollBarHeight / 2),
    )
  }
}
