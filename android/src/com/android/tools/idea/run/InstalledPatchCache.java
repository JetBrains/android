/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.android.ddmlib.IDevice;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;

public class InstalledPatchCache implements Disposable {
  private final DeviceStateCache<Long> myCache;

  public InstalledPatchCache() {
    myCache = new DeviceStateCache<Long>(this);
  }

  public long getInstalledArscTimestamp(@NotNull IDevice device, @NotNull String pkgName) {
    Long ts = myCache.get(device, pkgName);
    return ts == null ? 0 : ts;
  }

  public void setInstalledArscTimestamp(@NotNull IDevice device, @NotNull String pkgName, long timestamp) {
    myCache.put(device, pkgName, timestamp);
  }

  @Override
  public void dispose() {
  }
}
