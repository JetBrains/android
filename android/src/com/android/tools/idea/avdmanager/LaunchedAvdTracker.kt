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
package com.android.tools.idea.avdmanager

import com.intellij.openapi.components.Service
import io.ktor.util.collections.ConcurrentMap

/** Keeps track of emulator processes launched by Studio. */
@Service
class LaunchedAvdTracker {

  private val mutableLaunchedAvds = ConcurrentMap<String, ProcessHandle>()

  /**
   * A snapshot of all currently running emulator processes launched by Studio keyed by
   * [AVD IDs][com.android.sdklib.internal.avd.AvdInfo.id].
   */
  val launchedAvds: Map<String, ProcessHandle>
    get() = mutableLaunchedAvds.toMap()

  /**
   * Called when an emulator launched by Studio starts. The [avdId] parameter has the same semantics same as
   * [AvdInfo.id][com.android.sdklib.internal.avd.AvdInfo.id].
   */
  internal fun started(avdId: String, processHandle: ProcessHandle) {
    mutableLaunchedAvds.put(avdId, processHandle)
  }

  /**
   * Called when an emulator launched by Studio terminates. The [avdId] parameter has the same semantics same as
   * [AvdInfo.id][com.android.sdklib.internal.avd.AvdInfo.id].
   */
  internal fun terminated(avdId: String, processHandle: ProcessHandle) {
    mutableLaunchedAvds.remove(avdId, processHandle)
  }
}