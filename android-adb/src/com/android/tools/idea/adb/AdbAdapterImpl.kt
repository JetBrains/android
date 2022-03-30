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
package com.android.tools.idea.adb

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener
import com.android.ddmlib.IDevice
import com.intellij.openapi.project.Project
import kotlinx.coroutines.guava.await

/**
 * Implementation of [AdbAdapter]
 */
class AdbAdapterImpl(private val project: Project) : AdbAdapter {
  override suspend fun getDevices(): List<IDevice> = AdbService.getInstance().getDebugBridge(project).await().devices.asList()

  override fun addDeviceChangeListener(listener: IDeviceChangeListener) {
    AndroidDebugBridge.addDeviceChangeListener(listener)
  }

  override fun removeDeviceChangeListener(listener: IDeviceChangeListener) {
    AndroidDebugBridge.removeDeviceChangeListener(listener)
  }
}
