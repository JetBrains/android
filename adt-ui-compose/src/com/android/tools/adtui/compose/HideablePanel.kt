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

import androidx.compose.foundation.border
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.intellij.icons.AllIcons
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.modifier.border
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.util.thenIf

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
    Row(
      modifier =
        Modifier.thenIf(isFocused) {
            border(
              Stroke.Alignment.Outside,
              shape = RoundedCornerShape(4.dp),
              color = JewelTheme.globalColors.outlines.focused,
              width = JewelTheme.globalMetrics.outlineWidth,
            )
          }
          .onFocusChanged { isFocused = it.isFocused }
          .clickable { isOpen = !isOpen }
    ) {
      if (isOpen) {
        Icon("general/arrowDown.svg", "open", AllIcons::class.java)
      } else {
        Icon("general/arrowRight.svg", "closed", AllIcons::class.java)
      }
      Spacer(Modifier.padding(2.dp))
      Text(title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (isOpen) {
      content()
    }
  }
}
