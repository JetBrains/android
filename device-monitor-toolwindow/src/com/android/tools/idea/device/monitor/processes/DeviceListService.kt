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
package com.android.tools.idea.device.monitor.processes

import com.android.annotations.concurrency.UiThread

/**
 * Provides access to currently active [devices], and call into [DeviceListServiceListener] when the list changes.
 *
 * The service is meant to be called on the EDT thread, where long-running operations suspend,
 * while state changes (e.g. new device discovered, existing device disconnected, etc.) fire events on
 * the registered [DeviceListServiceListener] instances. Events are always fired on the EDT
 * thread.
 */
@UiThread
interface DeviceListService {
  /**
   * Returns the list of currently known devices.
   */
  val devices: List<Device>

  fun addListener(listener: DeviceListServiceListener)
  fun removeListener(listener: DeviceListServiceListener)

  /**
   * Starts the service, usually after registering one or more [DeviceListServiceListener].
   */
  suspend fun start()

  /**
   * Return a snapshot of the list of currently active [processes][ProcessInfo] on [device].
   */
  suspend fun fetchProcessList(device: Device): List<ProcessInfo>

  /**
   * Kills the [process] on the [device][ProcessInfo.device]
   */
  suspend fun killProcess(process: ProcessInfo)

  /**
   * Kills the [process] on the [device][ProcessInfo.device]
   */
  suspend fun forceStopProcess(process: ProcessInfo)
}
