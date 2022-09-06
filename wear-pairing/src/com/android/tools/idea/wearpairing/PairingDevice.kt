/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.wearpairing

import com.android.ddmlib.IDevice
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.project.Project

enum class ConnectionState {
  ONLINE, OFFLINE, DISCONNECTED
}

data class PairingDevice(
  val deviceID: String,
  val displayName: String,
  val apiLevel: Int,
  val isEmulator: Boolean,
  val isWearDevice: Boolean,
  val hasPlayStore: Boolean,
  val state: ConnectionState,
) {
  // This field is declared outside the main constructor because it should not be used for equals/hash. Kotlin doesn't have a better way.
  lateinit var launch: (Project?) -> ListenableFuture<IDevice>

  fun isOnline(): Boolean = state == ConnectionState.ONLINE

  fun disconnectedCopy(): PairingDevice {
    val res = copy(state = ConnectionState.DISCONNECTED)
    res.launch = { Futures.immediateFailedFuture(RuntimeException("DISCONNECTED")) }
    return res
  }

  override fun toString() = displayName
}
