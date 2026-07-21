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
package com.android.tools.adtui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.IconButton

/**
 * An IconButton decorated with a standard IntelliJ popup indicator badge (a small triangle in the bottom-right corner) to indicate that the
 * button triggers a dropdown or popup menu action.
 */
@Composable
fun DropdownIconButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  badgeSize: Dp = 4.dp,
  badgeColor: Color = if (enabled) JewelTheme.globalColors.text.info else JewelTheme.globalColors.text.disabled,
  icon: @Composable () -> Unit,
) {
  IconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
    Box(
      modifier =
        Modifier.drawWithCache {
          val triangleSize = badgeSize.toPx()
          val path =
            Path().apply {
              moveTo(size.width, size.height)
              lineTo(size.width - triangleSize, size.height)
              lineTo(size.width, size.height - triangleSize)
              close()
            }
          onDrawWithContent {
            drawContent()
            drawPath(path, color = badgeColor)
          }
        }
    ) {
      icon()
    }
  }
}
