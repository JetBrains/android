/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.avd.glassespairing

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.android.sdklib.AndroidVersion
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.sdklib.devices.Abi
import com.android.sdklib.getReleaseNameAndDetails
import com.intellij.util.ui.UIUtil
import icons.StudioIconsCompose
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.foundation.lazy.SingleSelectionLazyColumn
import org.jetbrains.jewel.foundation.lazy.SingleSelectionLazyListState
import org.jetbrains.jewel.foundation.lazy.items
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icon.IconKey

@Stable
internal data class DeviceRow(
  val handle: DeviceHandle,
  val state: DeviceState,
  val subtitle: String? = null,
  val isEnabled: Boolean = true,
) {
  val name: String = state.properties.title
  val icon: IconKey = state.properties.deviceType.toIcon()
  val androidVersion: AndroidVersion? = state.properties.androidVersion
  val abi: Abi? = state.properties.primaryAbi
  val isConnected = state is DeviceState.Connected
}

private fun DeviceType?.toIcon() =
  when (this) {
    DeviceType.HANDHELD -> StudioIconsCompose.DeviceExplorer.PhysicalDevicePhone
    DeviceType.WEAR -> StudioIconsCompose.DeviceExplorer.PhysicalDeviceWear
    DeviceType.TV -> StudioIconsCompose.DeviceExplorer.PhysicalDeviceTv
    DeviceType.AUTOMOTIVE -> StudioIconsCompose.DeviceExplorer.PhysicalDeviceCar
    DeviceType.XR_HEADSET -> StudioIconsCompose.DeviceExplorer.PhysicalDeviceHeadset
    DeviceType.AI_GLASSES -> StudioIconsCompose.DeviceExplorer.PhysicalDeviceGlass
    else -> StudioIconsCompose.DeviceExplorer.PhysicalDevicePhone
  }

@Composable
internal fun DeviceList(
  devices: ImmutableList<DeviceRow>,
  onSelectedDeviceChange: (DeviceRow) -> Unit,
  state: SingleSelectionLazyListState,
  modifier: Modifier = Modifier,
) {
  Box(modifier) {
    SingleSelectionLazyColumn(
      state = state,
      onSelectedIndexesChange = { indexes -> indexes.singleOrNull()?.let { onSelectedDeviceChange(devices[it]) } },
    ) {
      items(items = devices, key = { it.handle.id }, selectable = { it.isEnabled }) {
        DeviceRow(row = it, isSelected = isSelected, isFocused = isActive)
      }
    }

    VerticalScrollbar(
      adapter = rememberScrollbarAdapter(state.lazyListState),
      modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd),
    )
  }
}

@Composable
private fun DeviceRow(row: DeviceRow, isSelected: Boolean, isFocused: Boolean) {
  val alphaModifier = if (row.isEnabled) Modifier else Modifier.alpha(0.5f)

  Row(
    Modifier.background(UIUtil.getListBackground(isSelected, isFocused).toComposeColor())
      .fillMaxWidth()
      .padding(vertical = 4.dp)
      .semantics { if (!row.isEnabled) disabled() }
  ) {
    if (row.isConnected) {
      Icon(
        key = StudioIconsCompose.Avd.StatusDecoratorOnline,
        contentDescription = "online",
        Modifier.size(16.dp).align(Alignment.CenterVertically).then(alphaModifier),
      )
    } else {
      Spacer(Modifier.size(16.dp).align(Alignment.CenterVertically))
    }
    Icon(key = row.icon, contentDescription = null, Modifier.size(32.dp).padding(horizontal = 6.dp).then(alphaModifier))
    Column(Modifier.align(Alignment.CenterVertically).fillMaxWidth(), Arrangement.spacedBy(2.dp)) {
      with(row) {
        val nameColor = if (row.isEnabled) Color.Unspecified else JewelTheme.globalColors.text.disabled
        val detailsColor = if (row.isEnabled) JewelTheme.globalColors.text.info else JewelTheme.globalColors.text.disabled

        Text(name, color = nameColor)
        val detailsText =
          when {
            subtitle != null -> subtitle
            androidVersion != null -> androidVersion.toLabelText() + (abi?.cpuArch?.let { " | $it" } ?: "")
            else -> null
          }
        if (detailsText != null) {
          Text(detailsText, color = detailsColor, fontSize = LocalTextStyle.current.fontSize * 0.9)
        }
      }
    }
  }
}

private fun AndroidVersion.toLabelText(): String {
  val (name, details) = getReleaseNameAndDetails(includeCodeName = true)
  return name + (details?.let { " ($details)" } ?: "")
}
