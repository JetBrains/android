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
package com.android.tools.configurations

import com.android.ide.common.resources.Locale
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.devices.Device
import com.android.sdklib.internal.avd.AvdInfo
import com.google.common.collect.ImmutableList

/** Provides default values for [Configuration] fields and construction. */
interface ConfigurationSettings {
  val defaultDevice: Device?

  fun selectDevice(device: Device)

  var locale: Locale

  var target: IAndroidTarget?

  fun getTarget(minVersion: Int): IAndroidTarget?

  val stateVersion: Int

  val configModule: ConfigurationModelModule

  val resolverCache: ResourceResolverCache

  val localesInProject: ImmutableList<Locale>

  val devices: ImmutableList<Device>

  val projectTarget: IAndroidTarget?

  fun createDeviceForAvd(avd: AvdInfo): Device?

  val highestApiTarget: IAndroidTarget?

  val targets: Array<IAndroidTarget>

  fun getDeviceById(id: String): Device?

  val recentDevices: List<Device>

  val avdDevices: List<Device>
}