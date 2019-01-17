/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.datastore;

import com.android.tools.profiler.proto.Common;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * A semantic wrapper for the id of a device (serial + boot_id), which is just a long.
 */
public final class DeviceId {
  private static final Map<Long, DeviceId> ourInstances = new HashMap<>();
  private long myDeviceId;

  @NotNull
  public static DeviceId of(long deviceId) {
    return ourInstances.computeIfAbsent(deviceId, id -> new DeviceId(id));
  }

  @NotNull
  public static DeviceId fromSession(@NotNull Common.Session session) {
    return of(session.getDeviceId());
  }

  private DeviceId(long deviceId) {
    myDeviceId = deviceId;
  }

  public long get() {
    return myDeviceId;
  }

  @Override
  public int hashCode() {
    return Long.hashCode(myDeviceId);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof DeviceId)) {
      return false;
    }

    DeviceId other = (DeviceId)obj;
    return myDeviceId == other.myDeviceId;
  }
}
