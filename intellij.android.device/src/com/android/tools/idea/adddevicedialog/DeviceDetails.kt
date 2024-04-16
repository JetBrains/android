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
package com.android.tools.idea.adddevicedialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.tools.adtui.compose.DeviceScreenDiagram
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.ui.component.Text

@Composable
fun DeviceDetails(device: DeviceProfile, apiLevel: Int?, modifier: Modifier = Modifier) {
  Column(
    verticalArrangement = Arrangement.spacedBy(2.dp),
    modifier = modifier.padding(4.dp).verticalScroll(rememberScrollState()),
  ) {
    Text(
      device.name,
      fontWeight = FontWeight.Bold,
      fontSize = LocalTextStyle.current.fontSize * 1.2,
    )
    DeviceScreenDiagram(
      device.resolution.width,
      device.resolution.height,
      Modifier.widthIn(max = 200.dp),
    )
  }
}

@Composable
private fun Header(text: String) {
  Text(text, fontWeight = FontWeight.SemiBold, fontSize = LocalTextStyle.current.fontSize * 1.1)
}
