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
package com.android.tools.idea.compose.meshgradient.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.android.tools.idea.compose.meshgradient.toHexStringNoHash
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.DropdownState
import org.jetbrains.jewel.ui.component.MenuScope
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.styling.DropdownStyle
import org.jetbrains.jewel.ui.outline
import org.jetbrains.jewel.ui.theme.dropdownStyle

@Composable
fun ColorSwatch(color: Color, modifier: Modifier = Modifier) {
  Box(modifier.clip(RoundedCornerShape(4.dp)).size(16.dp)) {
    if (color == Color.Transparent) {
      Spacer(
        Modifier.drawBehind {
            drawIntoCanvas {
              drawPath(
                path =
                  Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, size.height)
                    close()
                  },
                color = Color.Red,
                style = Stroke(width = 2f),
              )
            }
          }
          .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
          .fillMaxSize()
      )
    } else Spacer(Modifier.fillMaxSize().background(color))
  }
}

@Composable
fun ColorDropdown(
  selectedColor: Color,
  colors: List<Color>,
  onSelected: (Color) -> Unit,
  modifier: Modifier = Modifier,
  allowTransparency: Boolean = false,
) {
  val focusManager = LocalFocusManager.current

  DropdownButton(
    modifier = modifier,
    menuModifier = Modifier.offset(x = (-2).dp),
    menuContent = {
      if (allowTransparency) {
        selectableItem(
          selected = selectedColor == Color.Transparent,
          onClick = {
            focusManager.clearFocus()
            onSelected(Color.Transparent)
          },
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            ColorSwatch(color = Color.Transparent)
            Spacer(Modifier.width(8.dp))
            Text("Transparent")
          }
        }
      }
      colors.forEach { color ->
        selectableItem(
          selected = selectedColor == color,
          onClick = {
            focusManager.clearFocus()
            onSelected(color)
          },
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            ColorSwatch(color = color)
            Spacer(Modifier.width(8.dp))
            Text(color.toHexStringNoHash(false))
          }
        }
      }
    },
  ) {
    ColorSwatch(color = selectedColor, modifier = Modifier.padding(vertical = 5.dp, horizontal = 8.dp))
  }
}

@Composable
private fun DropdownButton(
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  menuModifier: Modifier = Modifier,
  outline: Outline = Outline.None,
  interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
  style: DropdownStyle = JewelTheme.dropdownStyle,
  menuContent: MenuScope.() -> Unit,
  content: @Composable BoxScope.() -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  var skipNextClick by remember { mutableStateOf(false) }

  var dropdownState by remember(interactionSource) { mutableStateOf(DropdownState.of(enabled = enabled)) }

  LaunchedEffect(enabled) { dropdownState = dropdownState.copy(enabled = enabled) }

  LaunchedEffect(interactionSource) {
    interactionSource.interactions.collect { interaction ->
      when (interaction) {
        is PressInteraction.Press -> dropdownState = dropdownState.copy(pressed = true)
        is PressInteraction.Cancel,
        is PressInteraction.Release -> dropdownState = dropdownState.copy(pressed = false)
        is HoverInteraction.Enter -> dropdownState = dropdownState.copy(hovered = true)
        is HoverInteraction.Exit -> dropdownState = dropdownState.copy(hovered = false)
        is FocusInteraction.Focus -> dropdownState = dropdownState.copy(focused = true)
        is FocusInteraction.Unfocus -> dropdownState = dropdownState.copy(focused = false)
      }
    }
  }

  val colors = style.colors
  val metrics = style.metrics
  val minSize = metrics.minSize
  val shape = RoundedCornerShape(style.metrics.cornerSize)

  var componentWidth by remember { mutableIntStateOf(-1) }
  Box(
    modifier =
      modifier
        .clickable(
          onClick = {
            if (!skipNextClick) {
              expanded = !expanded
            }
            skipNextClick = false
          },
          enabled = enabled,
          role = Role.Button,
          interactionSource = interactionSource,
          indication = null,
        )
        .background(colors.backgroundFor(dropdownState).value, shape)
        .outline(dropdownState, outline, shape)
        .defaultMinSize(minHeight = minSize.height)
        .onSizeChanged { componentWidth = it.width },
    contentAlignment = Alignment.CenterStart,
  ) {
    CompositionLocalProvider(
      LocalContentColor provides colors.contentFor(dropdownState).value,
      LocalTextStyle provides LocalTextStyle.current.copy(color = colors.contentFor(dropdownState).value),
    ) {
      Box(contentAlignment = Alignment.Center, content = content)
    }

    if (expanded) {
      val density = LocalDensity.current
      PopupMenu(
        onDismissRequest = {
          expanded = false
          if (it == InputMode.Touch && dropdownState.isHovered) {
            skipNextClick = true
          }
          true
        },
        modifier = menuModifier.focusProperties { canFocus = true }.defaultMinSize(minWidth = with(density) { componentWidth.toDp() }),
        menuStyle = style.menuStyle,
        horizontalAlignment = Alignment.Start,
        content = menuContent,
      )
    }
  }
}
