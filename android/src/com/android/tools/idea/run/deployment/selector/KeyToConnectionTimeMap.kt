/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.selector

import com.android.sdklib.deviceprovisioner.DeviceId
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

internal class KeyToConnectionTimeMap(private val clock: Clock = Clock.systemDefaultZone()) {
  private val map = ConcurrentHashMap<DeviceId, Instant>()

  operator fun get(key: DeviceId): Instant {
    return map.computeIfAbsent(key) { clock.instant() }
  }

  fun retainAll(keys: Collection<DeviceId>) {
    map.keys.retainAll(keys)
  }
}
