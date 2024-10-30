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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.repository.api.RemotePackage
import com.android.sdklib.ISystemImage
import com.android.sdklib.RemoteSystemImage
import com.android.sdklib.devices.Storage
import com.android.tools.adtui.compose.DeviceScreenDiagram
import com.google.common.collect.Range
import com.intellij.util.ui.JBUI
import java.text.DecimalFormat
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/**
 * A panel showing a summary of a [DeviceProfile] and (optionally) a system image. Contains a
 * diagram of the screen, along with various metadata in a tabular format.
 */
@Composable
fun DeviceDetails(
  device: DeviceProfile,
  modifier: Modifier = Modifier,
  systemImage: ISystemImage? = null,
) {
  VerticallyScrollableContainer(modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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

      if (systemImage != null && systemImage is RemoteSystemImage) {
        val imageSize = (systemImage.`package` as? RemotePackage)?.archive?.complete?.size
        InfoBanner(
          text =
            (if (imageSize == null) "System" else Storage(imageSize).toUiString() + " system") +
              " image will be downloaded",
          AllIconsKeys.Actions.Download,
          Modifier.padding(vertical = 4.dp),
        )
      }

      Header("Device")
      LabeledValue("OEM", device.manufacturer)
      if (device.apiRange.isSingleton() && systemImage == null) {
        LabeledValue("API Level", device.apiRange.lowerEndpoint().toString())
      } else {
        TwoLineLabeledValue(
          "Supported API Levels",
          device.apiRange.firstAndLastApiLevel(),
          Modifier.padding(top = 4.dp),
        )
      }

      if (systemImage != null) {
        Header("System Image")
        LabeledValue("API Level", systemImage.androidVersion.apiLevel.toString())
        LabeledValue("Services", systemImage.services)
        LabeledValue("ABI", systemImage.abiTypes.joinToString(", "))
        if (systemImage.translatedAbiTypes.isNotEmpty()) {
          LabeledValue("Translated ABI", systemImage.translatedAbiTypes.joinToString(", "))
        }
      }

      Header("Screen")
      LabeledValue("Resolution", device.resolution.toString())
      LabeledValue("Density", "${device.displayDensity} dpi")
    }
  }
}

internal val ISystemImage.services: String
  get() =
    when {
      hasPlayStore() -> "Google Play"
      hasGoogleApis() -> "Google APIs"
      else -> "Android Open Source"
    }

private fun Range<Int>.isSingleton(): Boolean =
  hasLowerBound() && hasUpperBound() && lowerEndpoint() == upperEndpoint()

internal fun Range<Int>.firstAndLastApiLevel(): String =
  if (hasUpperBound()) {
    if (lowerEndpoint() == upperEndpoint()) "${lowerEndpoint()}"
    else "${lowerEndpoint()}\u2013${upperEndpoint()}"
  } else "${lowerEndpoint()}+"

@Composable
private fun Header(text: String) {
  Text(
    text,
    fontWeight = FontWeight.SemiBold,
    fontSize = LocalTextStyle.current.fontSize * 1.1,
    modifier = Modifier.padding(top = 4.dp).semantics { heading() },
  )
}

@Composable
private fun InfoBanner(text: String, iconKey: IconKey, modifier: Modifier = Modifier) {
  val borderColor = JBUI.CurrentTheme.Banner.INFO_BORDER_COLOR.toComposeColor()
  val backgroundColor = JBUI.CurrentTheme.Banner.INFO_BACKGROUND.toComposeColor()
  Column(modifier.fillMaxWidth()) {
    Divider(orientation = Orientation.Horizontal, color = borderColor)
    Row(
      modifier = Modifier.background(backgroundColor).padding(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(iconKey, contentDescription = null)
      Spacer(modifier = Modifier.width(8.dp))
      Text(text = text)
    }
    Divider(orientation = Orientation.Horizontal, color = borderColor)
  }
}

@Composable
private fun LabeledValue(label: String, value: String) {
  Row {
    Column(Modifier.weight(0.5f).alignByBaseline()) {
      Text(label, color = JewelTheme.globalColors.text.info)
    }
    Spacer(Modifier.size(4.dp))
    Column(Modifier.weight(0.5f).alignByBaseline()) { Text(value) }
  }
}

@Composable
private fun TwoLineLabeledValue(label: String, value: String, modifier: Modifier = Modifier) {
  Column(modifier) {
    Text(label, color = JewelTheme.globalColors.text.info)
    Text(value)
  }
}

private val diagonalLengthFormat = DecimalFormat(".##")

private fun DeviceProfile.diagonalLengthString() =
  if (displayDiagonalLength > 0) diagonalLengthFormat.format(displayDiagonalLength) + '\u2033'
  else ""
