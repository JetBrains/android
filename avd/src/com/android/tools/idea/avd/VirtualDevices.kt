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
package com.android.tools.idea.avd

import com.android.sdklib.ISystemImage
import com.android.sdklib.internal.avd.AvdManager
import com.android.sdklib.internal.avd.AvdNames
import com.android.sdklib.internal.avd.uniquifyAvdName
import java.nio.file.Files
import java.nio.file.Path

internal class VirtualDevices(private val avdManager: AvdManager) {
  internal fun add(device: VirtualDevice, image: ISystemImage) {
    val avdBuilder = avdManager.createAvdBuilder(device.device)
    avdBuilder.copyFrom(device, image)
    avdBuilder.avdName = avdManager.uniquifyAvdName(AvdNames.cleanAvdName(device.name))
    avdBuilder.avdFolder = avdBuilder.avdFolder.parent.uniquifyAvdFolder(avdBuilder.avdName)
    avdBuilder.displayName = device.name

    avdManager.createAvd(avdBuilder)
  }
}

private fun Path.uniquifyAvdFolder(name: String): Path =
  resolve(AvdNames.uniquify(name, "_") { Files.exists(resolve("$it.avd")) } + ".avd")
