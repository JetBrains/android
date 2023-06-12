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
package com.android.tools.idea.adddevicedialog

import com.android.sdklib.devices.Device
import com.android.tools.idea.avdmanager.DeviceManagerConnection
import com.android.tools.idea.device.Resolution
import com.ibm.icu.text.Collator
import com.ibm.icu.util.ULocale

internal class Definition private constructor(device: Device) : Comparable<Definition> {
  internal val name = device.displayName
  internal val size = device.defaultHardware.screen.diagonalLength
  internal val resolution = device.getScreenSize(device.defaultState.orientation)!!.let { Resolution(it.width, it.height) }
  internal val density = device.defaultHardware.screen.pixelDensity.resourceValue

  override fun compareTo(other: Definition) = COLLATOR.compare(name, other.name)

  internal companion object {
    private val COLLATOR: Collator = Collator.getInstance(ULocale.ROOT).freeze()

    internal fun getDefinitions(): Collection<Definition> {
      return DeviceManagerConnection.getDefaultDeviceManagerConnection().devices.asSequence()
        .filterNot(Device::getIsDeprecated)
        .map(::Definition)
        .toList()
    }
  }
}
