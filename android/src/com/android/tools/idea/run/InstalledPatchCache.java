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
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InstalledPatchCache implements Disposable {
  private final DeviceStateCache<PatchState> myCache;

  public InstalledPatchCache() {
    myCache = new DeviceStateCache<PatchState>(this);
  }

  public long getInstalledArscTimestamp(@NotNull IDevice device, @NotNull String pkgName) {
    PatchState state = getState(device, pkgName, false);
    return state == null ? 0 : state.resourcesModified;
  }

  public void setInstalledArscTimestamp(@NotNull IDevice device, @NotNull String pkgName, long timestamp) {
    getState(device, pkgName, true).resourcesModified = timestamp;
  }

  public long getInstalledManifestTimestamp(@NotNull IDevice device, @NotNull String pkgName) {
    PatchState state = getState(device, pkgName, false);
    return state == null ? 0 : state.manifestModified;
  }

  public void setInstalledManifestTimestamp(@NotNull IDevice device, @NotNull String pkgName, long timestamp) {
    getState(device, pkgName, true).manifestModified = timestamp;
  }

  @Contract("!null, !null, true -> !null")
  @Nullable
  private PatchState getState(@NotNull IDevice device, @NotNull String pkgName, boolean create) {
    PatchState state = myCache.get(device, pkgName);
    if (state == null && create) {
      state = new PatchState();
      myCache.put(device, pkgName, state);
    }
    return state;
  }

  @Override
  public void dispose() {
  }

  private static class PatchState {
    public long resourcesModified;
    public long manifestModified;
  }
}
