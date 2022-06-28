/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.device.screenshot

import com.android.tools.adtui.device.DeviceArtDescriptor
import com.android.tools.idea.ui.screenshot.FramingOption
import java.nio.file.Path

/**
 * Screenshot framing option based either on a device skin or on a [DeviceArtDescriptor].
 * The [skinFolder] and [deviceArtDescriptor] properties are mutually exclusive. One of them
 * is guaranteed to be not null.
 */
@Suppress("DataClassPrivateConstructor")
data class DeviceFramingOption private constructor(
  override val displayName: String,
  val skinFolder: Path?,
  val deviceArtDescriptor: DeviceArtDescriptor?
) : FramingOption {

  constructor(displayName: String, skinFolder: Path) : this(displayName, skinFolder, null)
  constructor(deviceArtDescriptor: DeviceArtDescriptor) : this(deviceArtDescriptor.name, null, deviceArtDescriptor)
}