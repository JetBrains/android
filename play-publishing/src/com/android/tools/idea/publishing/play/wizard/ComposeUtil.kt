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
package com.android.tools.idea.publishing.play.wizard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import icons.StudioIllustrationsCompose
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text

@Composable
fun PlayPublishingWizardHeader(subtitle: String? = null) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.Bottom) {
      Icon(key = StudioIllustrationsCompose.Common.PlayConsoleIcon, contentDescription = null, modifier = Modifier.size(24.dp))
      Spacer(modifier = Modifier.width(12.dp))
      Text(
        text = "Publish your Android app for testing",
        style = JewelTheme.defaultTextStyle.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
      )
      if (subtitle != null) {
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = subtitle, style = JewelTheme.defaultTextStyle.copy(fontSize = 16.sp, color = Color.Gray))
      }
    }
    Divider(Orientation.Horizontal, Modifier.fillMaxWidth())
  }
}

@Composable
fun FormField(label: String, content: @Composable () -> Unit) {
  Row(verticalAlignment = Alignment.Top) {
    Text(text = label, modifier = Modifier.width(150.dp).padding(top = 10.dp), style = JewelTheme.defaultTextStyle.copy(fontSize = 13.sp))
    Box(modifier = Modifier.weight(1f).padding(vertical = 2.dp)) { content() }
  }
}
