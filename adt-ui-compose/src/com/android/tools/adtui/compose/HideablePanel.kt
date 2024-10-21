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
package com.android.tools.adtui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.styling.LocalGroupHeaderStyle
import org.jetbrains.jewel.ui.focusOutline
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/**
 * A panel that can be opened or closed. It has a header with a title and an arrow indicating
 * open/close state.
 */
@Composable
fun HideablePanel(
  title: String,
  modifier: Modifier = Modifier,
  initiallyOpen: Boolean = true,
  content: @Composable () -> Unit,
) {
  var isOpen by remember { mutableStateOf(initiallyOpen) }
  var isFocused by remember { mutableStateOf(false) }
  Column(modifier) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Row(
        modifier =
          Modifier.semantics(mergeDescendants = true) { heading() }
            .focusOutline(isFocused, RoundedCornerShape(2.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(interactionSource = null, indication = null) { isOpen = !isOpen }
            .padding(vertical = 6.dp)
      ) {
        if (isOpen) {
          Icon(AllIconsKeys.General.ArrowDown, "open")
        } else {
          Icon(AllIconsKeys.General.ArrowRight, "closed")
        }
        Spacer(Modifier.padding(8.dp))
        Text(title)
        Spacer(Modifier.padding(4.dp))
      }
      Spacer(Modifier.padding(4.dp))
      val groupHeaderStyle = LocalGroupHeaderStyle.current
      Divider(
        orientation = Orientation.Horizontal,
        color = groupHeaderStyle.colors.divider,
        thickness = groupHeaderStyle.metrics.dividerThickness,
        startIndent = groupHeaderStyle.metrics.indent,
      )
    }
    if (isOpen) {
      content()
    }
  }
}
