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
package com.android.tools.idea.glassespairing

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.sdklib.AndroidVersion
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.sdklib.devices.Abi
import com.android.sdklib.getReleaseNameAndDetails
import com.intellij.util.ui.UIUtil
import icons.StudioIconsCompose
import java.text.Collator
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.foundation.lazy.SelectableLazyColumn
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListState
import org.jetbrains.jewel.foundation.lazy.SelectionMode
import org.jetbrains.jewel.foundation.lazy.items
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icon.IconKey

@Stable
internal data class DeviceRow(val handle: DeviceHandle, val state: DeviceState) {
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
  state: SelectableLazyListState,
) {
  val devices = devices.sortedWith(compareBy(Collator.getInstance(), { it.name }))
  Box(Modifier.fillMaxSize()) {
    SelectableLazyColumn(
      selectionMode = SelectionMode.Single,
      state = state,
      onSelectedIndexesChange = { indices ->
        indices.singleOrNull()?.let { onSelectedDeviceChange(devices[it]) }
      },
    ) {
      items(devices, key = { it }) {
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
  Row(
    Modifier.background(UIUtil.getListBackground(isSelected, isFocused).toComposeColor())
      .fillMaxWidth()
      .padding(vertical = 4.dp)
  ) {
    if (row.isConnected) {
      Icon(
        key = StudioIconsCompose.Avd.StatusDecoratorOnline,
        contentDescription = "online",
        Modifier.size(16.dp).align(Alignment.CenterVertically),
      )
    } else {
      Spacer(Modifier.size(16.dp).align(Alignment.CenterVertically))
    }
    Icon(key = row.icon, contentDescription = null, Modifier.size(32.dp).padding(horizontal = 6.dp))
    Column(Modifier.align(Alignment.CenterVertically).fillMaxWidth(), Arrangement.spacedBy(2.dp)) {
      with(row) {
        Text(name)
        if (androidVersion != null) {
          Text(
            androidVersion.toLabelText() + (abi?.cpuArch?.let { " | $it" } ?: ""),
            color = JewelTheme.globalColors.text.info,
            fontSize = LocalTextStyle.current.fontSize * 0.9,
          )
        }
      }
    }
  }
}

private fun AndroidVersion.toLabelText(): String {
  val (name, details) = getReleaseNameAndDetails(includeCodeName = true)
  return name + (details?.let { " ($details)" } ?: "")
}
