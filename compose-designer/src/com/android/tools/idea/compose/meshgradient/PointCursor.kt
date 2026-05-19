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
package com.android.tools.idea.compose.meshgradient

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.Text

// Adapted from the Mesh project: des/c5inco/mesh/common/PointCursor.kt

@Composable
fun PointCursor(xIndex: Int, yIndex: Int, color: Color, modifier: Modifier = Modifier) {
  Box(
    contentAlignment = Alignment.Center,
    modifier =
      modifier.size(20.dp).drawWithContent {
        drawContent()
        drawCircle(color = color)
        drawCircle(color = Color.White, style = Stroke(width = 4.dp.toPx())) // Fill is transparent by default
      },
  ) {
    Text("$xIndex,$yIndex", color = Color.White)
  }
}
