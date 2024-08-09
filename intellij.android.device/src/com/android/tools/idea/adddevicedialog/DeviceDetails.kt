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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.tools.adtui.compose.DeviceScreenDiagram
import java.text.DecimalFormat
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.ui.component.Text

@Composable
fun DeviceDetails(
  device: DeviceProfile,
  apiLevelSelection: ApiLevelSelection,
  modifier: Modifier = Modifier,
) {
  Column(
    verticalArrangement = Arrangement.spacedBy(4.dp),
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
      diagonalLength = device.diagonalLengthString(),
      round = device.isRound,
      modifier =
        Modifier.widthIn(max = 200.dp).heightIn(max = 200.dp).align(Alignment.CenterHorizontally),
    )

    Header("Device")
    LabeledValue("OEM", device.manufacturer)
    LabeledValue("Density", "${device.displayDensity} dpi")

    Header("System Image")
    if (device.apiLevels.size > 1) {
      LabeledValue("Selected API", apiLevelSelection.apply(device).toString())
      LabeledValue(
        "Supported APIs",
        // TODO: Join to a range?
        device.apiLevels.map { it.apiStringWithoutExtension }.joinToString(", "),
      )
    } else {
      LabeledValue("API", device.apiLevels.last().toString())
    }
    LabeledValue("Primary ABI", device.abis.firstOrNull()?.toString() ?: "Unknown")
    if (device.abis.size > 1) {
      LabeledValue("Additional ABIs", device.abis.drop(1).joinToString(","))
    }

    Header("Screen")
    LabeledValue("Resolution", device.resolution.toString())
    LabeledValue("Density", "${device.displayDensity} dpi")
  }
}

@Composable
private fun Header(text: String) {
  Text(
    text,
    fontWeight = FontWeight.SemiBold,
    fontSize = LocalTextStyle.current.fontSize * 1.1,
    modifier = Modifier.padding(top = 4.dp),
  )
}

@Composable
private fun LabeledValue(label: String, value: String) {
  Row {
    Column(Modifier.weight(0.5f)) { Text(label, fontWeight = FontWeight.Light) }
    Spacer(Modifier.size(4.dp))
    Column(Modifier.weight(0.5f)) { Text(value) }
  }
}

private val diagonalLengthFormat = DecimalFormat(".##")

private fun DeviceProfile.diagonalLengthString() =
  if (displayDiagonalLength > 0) diagonalLengthFormat.format(displayDiagonalLength) + '\u2033'
  else ""
