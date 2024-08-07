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
package com.android.tools.profilers.taskbased.common.icons

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import icons.StudioIcons
import icons.StudioIconsCompose
import javax.swing.Icon

object DeviceIconUtils {
  @Composable
  fun getDeviceIconPainter(swingIcon: Icon?): Painter? {
    val resourcePainterProvider = when (swingIcon) {
      // Phone + Tablet
      StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE -> StudioIconsCompose.DeviceExplorer.VirtualDevicePhone()
      StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE -> StudioIconsCompose.DeviceExplorer.PhysicalDevicePhone()
      StudioIcons.DeviceExplorer.FIREBASE_DEVICE_PHONE -> StudioIconsCompose.DeviceExplorer.FirebaseDevicePhone()
      // Watch
      StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_WEAR -> StudioIconsCompose.DeviceExplorer.VirtualDeviceWear()
      StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_WEAR -> StudioIconsCompose.DeviceExplorer.PhysicalDeviceWear()
      StudioIcons.DeviceExplorer.FIREBASE_DEVICE_WEAR -> StudioIconsCompose.DeviceExplorer.FirebaseDeviceWear()
      // TV
      StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_TV -> StudioIconsCompose.DeviceExplorer.VirtualDeviceTv()
      StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_TV -> StudioIconsCompose.DeviceExplorer.PhysicalDeviceTv()
      StudioIcons.DeviceExplorer.FIREBASE_DEVICE_TV -> StudioIconsCompose.DeviceExplorer.FirebaseDeviceTv()
      // Auto
      StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_CAR -> StudioIconsCompose.DeviceExplorer.VirtualDeviceCar()
      StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_CAR -> StudioIconsCompose.DeviceExplorer.PhysicalDeviceCar()
      StudioIcons.DeviceExplorer.FIREBASE_DEVICE_CAR -> StudioIconsCompose.DeviceExplorer.FirebaseDeviceCar()
      // Icon not found
      else -> null
    }
    return resourcePainterProvider?.getPainter()?.value
  }
}