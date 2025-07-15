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
package com.android.tools.idea.testartifacts.instrumented.testsuite.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.tools.adtui.compose.IntUiPaletteDefaults
import kotlin.math.abs
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.modifier.border
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.icon.IntelliJIconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

private const val DEFAULT_MAX_VISIBLE_ITEMS = 2

@Composable
fun Carousel(
  modifier: Modifier,
  itemCount: Int,
  listState: LazyListState = rememberLazyListState(),
  itemWidth: Dp?,
  maxVisibleItems: Int = DEFAULT_MAX_VISIBLE_ITEMS,
  itemContent: @Composable (Int) -> Unit,
) {
  val gapWidth = 8.dp
  val canNavigate = itemCount > maxVisibleItems
  val coroutineScope = rememberCoroutineScope()
  val mostInViewIndex by rememberMostInViewItemIndex(listState, itemCount)

  val initialWidth =
    if (itemWidth != null) {
      if (itemCount >= maxVisibleItems) {
        itemWidth * maxVisibleItems + gapWidth
      } else {
        itemWidth
      }
    } else {
      0.dp
    }
  var carouselWidth by
    remember(initialWidth.value.toInt()) { mutableIntStateOf(initialWidth.value.toInt()) }

  val resolvedItemWidth =
    itemWidth
      ?: with(LocalDensity.current) {
        if (itemCount >= maxVisibleItems) {
          ((carouselWidth.toDp() - (if (maxVisibleItems > 1) gapWidth else 0.dp)) / maxVisibleItems)
        } else {
          carouselWidth.toDp()
        }
      }

  Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.padding(8.dp)) {
    if (canNavigate) {
      // Left navigation button
      CarouselArrow(
        iconKey = AllIconsKeys.General.ArrowLeft,
        contentDescription = "Previous",
        enabled = listState.canScrollBackward,
        onClick = {
          coroutineScope.launch {
            listState.animateScrollToItem((listState.firstVisibleItemIndex - 1).coerceAtLeast(0))
          }
        },
      )
    }

    val contentModifier =
      if (itemWidth != null) {
        Modifier.width(carouselWidth.dp)
      } else {
        Modifier.weight(1f).onSizeChanged { size -> carouselWidth = size.width }
      }
    Column(modifier = contentModifier) {
      LazyRow(
        state = listState,
        modifier = Modifier.weight(1f),
        horizontalArrangement = Arrangement.spacedBy(gapWidth),
      ) {
        items(count = itemCount) { index ->
          Box(
            modifier = Modifier.width(resolvedItemWidth).fillMaxHeight(),
            contentAlignment = Alignment.Center,
          ) {
            itemContent(index)
          }
        }
      }

      if (itemCount > 1) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          modifier = Modifier.align(Alignment.CenterHorizontally),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          repeat(itemCount) { index ->
            val isSelected = mostInViewIndex == index
            val borderColor =
              rememberColor(IntUiPaletteDefaults.Dark.Gray11, IntUiPaletteDefaults.Light.Gray8)
            Box(
              modifier =
                Modifier.size(6.dp)
                  .border(
                    width = 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(size = 8.dp),
                  )
                  .clip(CircleShape)
                  .background(color = if (isSelected) borderColor else Color.Transparent)
                  .clickable { coroutineScope.launch { listState.animateScrollToItem(index) } }
            )
          }
        }
      }
    }

    if (canNavigate) {
      // Right navigation button
      CarouselArrow(
        iconKey = AllIconsKeys.General.ArrowRight,
        contentDescription = "Next",
        enabled = listState.canScrollForward,
        onClick = {
          coroutineScope.launch {
            listState.animateScrollToItem(
              (listState.firstVisibleItemIndex + 1).coerceAtMost(itemCount - 1)
            )
          }
        },
      )
    }
  }
}

@Composable
private fun rememberMostInViewItemIndex(listState: LazyListState, itemCount: Int): State<Int> {
  if (itemCount == 0) {
    return remember { mutableIntStateOf(-1) }
  }

  return remember(listState, itemCount) {
    derivedStateOf {
      val layoutInfo = listState.layoutInfo
      val visibleItemsInfo = layoutInfo.visibleItemsInfo

      if (visibleItemsInfo.isEmpty()) {
        // If no items are currently visible in layoutInfo (e.g., during initial composition
        // or if list is empty), fall back to the firstVisibleItemIndex, coerced to valid range
        listState.firstVisibleItemIndex.coerceIn(0, itemCount - 1)
      } else {
        val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2

        // Find the item whose center is closest to the viewport center
        val closestItem =
          visibleItemsInfo.minByOrNull { itemInfo ->
            val itemCenter = itemInfo.offset + itemInfo.size / 2
            abs(itemCenter - viewportCenter)
          }

        closestItem?.index ?: listState.firstVisibleItemIndex.coerceIn(0, itemCount - 1)
      }
    }
  }
}

@Composable
private fun CarouselArrow(
  iconKey: IntelliJIconKey,
  contentDescription: String,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  val enabledTintColor =
    rememberColor(IntUiPaletteDefaults.Dark.Gray11, IntUiPaletteDefaults.Light.Gray7)
  val disabledTintColor =
    rememberColor(IntUiPaletteDefaults.Dark.Gray3, IntUiPaletteDefaults.Light.Gray12)
  val tintColor = if (enabled) enabledTintColor else disabledTintColor

  IconButton(
    modifier =
      Modifier.size(24.dp)
        .clip(CircleShape)
        .border(
          alignment = Stroke.Alignment.Inside,
          width = 1.dp,
          color = rememberColor(IntUiPaletteDefaults.Dark.Gray3, IntUiPaletteDefaults.Light.Gray12),
          shape = CircleShape,
        ),
    onClick = { onClick() },
    enabled = enabled,
  ) {
    Icon(
      key = iconKey,
      contentDescription = contentDescription,
      modifier = Modifier.size(16.dp),
      tint = tintColor,
    )
  }
}

@Composable
private fun rememberColor(dark: Int, light: Int): Color {
  val isDark = JewelTheme.isDark
  return remember(isDark) { if (isDark) Color(dark) else Color(light) }
}
