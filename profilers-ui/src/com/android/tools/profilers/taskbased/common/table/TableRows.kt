/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.profilers.taskbased.common.table

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.Text

@Composable
fun EllipsesText(text: String) {
  Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 5.dp))
}

@Composable
fun leftAlignedColumnText(text: String, rowScope: RowScope) {
  with(rowScope) {
    Box(
      modifier = Modifier.weight(1f).fillMaxHeight(),
      contentAlignment = Alignment.CenterStart
    ) {
      EllipsesText(text = text)
    }
  }
}

@Composable
fun rightAlignedColumnText(text: String, colWidth: Dp) {
  Box(
    modifier = Modifier.width(colWidth).fillMaxHeight(),
    contentAlignment = Alignment.CenterEnd
  ) {
    EllipsesText(text = text)
  }
}