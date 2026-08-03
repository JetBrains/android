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
package com.android.tools.profilers.taskbased.common.dropdowns

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.GlobalColors
import org.jetbrains.jewel.foundation.LocalGlobalColors
import org.jetbrains.jewel.foundation.OutlineColors
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.MenuScope
import org.jetbrains.jewel.ui.component.styling.DropdownColors
import org.jetbrains.jewel.ui.component.styling.DropdownMetrics
import org.jetbrains.jewel.ui.component.styling.DropdownStyle
import org.jetbrains.jewel.ui.component.styling.LocalUndecoratedDropdownStyle

@Composable
fun ProfilerComposeDropdown(modifier: Modifier = Modifier, menuContent: MenuScope.() -> Unit, content: @Composable BoxScope.() -> Unit) {
  val currentStyle = LocalUndecoratedDropdownStyle.current
  val customStyle =
    DropdownStyle(
      colors =
        DropdownColors(
          background = Color.Transparent,
          backgroundDisabled = Color.Transparent,
          backgroundFocused = Color.Transparent,
          backgroundPressed = currentStyle.colors.backgroundPressed,
          backgroundHovered = currentStyle.colors.backgroundHovered,
          content = currentStyle.colors.content,
          contentDisabled = currentStyle.colors.contentDisabled,
          contentFocused = currentStyle.colors.contentFocused,
          contentPressed = currentStyle.colors.contentPressed,
          contentHovered = currentStyle.colors.contentHovered,
          border = currentStyle.colors.border,
          borderDisabled = currentStyle.colors.borderDisabled,
          borderFocused = currentStyle.colors.borderFocused,
          borderPressed = currentStyle.colors.borderPressed,
          borderHovered = currentStyle.colors.borderHovered,
          iconTint = currentStyle.colors.iconTint,
          iconTintDisabled = currentStyle.colors.iconTintDisabled,
          iconTintFocused = currentStyle.colors.iconTintFocused,
          iconTintPressed = currentStyle.colors.iconTintPressed,
          iconTintHovered = currentStyle.colors.iconTintHovered,
        ),
      metrics =
        DropdownMetrics(
          arrowMinSize = DpSize(16.dp, 24.dp),
          minSize = DpSize(24.dp, 24.dp),
          cornerSize = currentStyle.metrics.cornerSize,
          contentPadding = PaddingValues(start = 8.dp, end = 4.dp),
          borderWidth = currentStyle.metrics.borderWidth,
        ),
      icons = currentStyle.icons,
      menuStyle = currentStyle.menuStyle,
    )

  val currentColors = LocalGlobalColors.current
  val noFocusOutlineColors =
    GlobalColors(
      borders = currentColors.borders,
      outlines =
        OutlineColors(
          focused = Color.Transparent,
          focusedWarning = currentColors.outlines.focusedWarning,
          focusedError = currentColors.outlines.focusedError,
          warning = currentColors.outlines.warning,
          error = currentColors.outlines.error,
        ),
      text = currentColors.text,
      panelBackground = currentColors.panelBackground,
      toolwindowBackground = currentColors.toolwindowBackground,
    )

  CompositionLocalProvider(LocalGlobalColors provides noFocusOutlineColors) {
    Dropdown(modifier = modifier, style = customStyle, menuContent = menuContent, content = content)
  }
}
