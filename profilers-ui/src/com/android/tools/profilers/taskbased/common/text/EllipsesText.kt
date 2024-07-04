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
package com.android.tools.profilers.taskbased.common.text

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EllipsisText(modifier: Modifier = Modifier,
                 text: String,
                 maxLines: Int = 1,
                 fontSize: TextUnit = TextUnit.Unspecified,
                 fontWeight: FontWeight? = null,
                 lineHeight: TextUnit = TextUnit.Unspecified,
                 color: Color = Color.Unspecified) {
  var hasVisualOverflow by remember { mutableStateOf(false) }
  if (hasVisualOverflow) {
    Tooltip(tooltip = { Text(text) }) {
      EllipsisTextContent(text, maxLines, fontSize, fontWeight, lineHeight, color, modifier) { hasVisualOverflow = it.hasVisualOverflow }
    }
  }
  else {
    EllipsisTextContent(text, maxLines, fontSize, fontWeight, lineHeight, color, modifier) { hasVisualOverflow = it.hasVisualOverflow }
  }
}

@Composable
private fun EllipsisTextContent(text: String,
                                maxLines: Int,
                                fontSize: TextUnit,
                                fontWeight: FontWeight?,
                                lineHeight: TextUnit,
                                color: Color,
                                modifier: Modifier,
                                onTextLayout: (TextLayoutResult) -> Unit) {
  Text(text = text, maxLines = maxLines, overflow = TextOverflow.Ellipsis, fontSize = fontSize, fontWeight = fontWeight,
       lineHeight = lineHeight, color = color, modifier = modifier, onTextLayout = onTextLayout)
}