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
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.ResourceUrl;
import com.android.tools.idea.fd.InstantRunManager;
import com.android.tools.idea.res.AppResourceRepository;
import com.android.utils.XmlUtils;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.*;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.google.common.base.Charsets.UTF_8;

public class InstalledPatchCache implements Disposable {
  private final DeviceStateCache<PatchState> myCache;

  public InstalledPatchCache() {
    myCache = new DeviceStateCache<>(this);
  }

  @Nullable
  public HashCode getInstalledManifestTimestamp(@NotNull IDevice device, @NotNull String pkgName) {
    PatchState state = getState(device, pkgName, false);
    return state == null ? null : state.manifest;
  }

  public void setInstalledManifestTimestamp(@NotNull IDevice device, @NotNull String pkgName, @NotNull HashCode hashCode) {
    getState(device, pkgName, true).manifest = hashCode;
  }

  @Nullable
  public HashCode getInstalledManifestResourcesHash(@NotNull IDevice device, @NotNull String pkgName) {
    PatchState state = getState(device, pkgName, false);
    return state == null ? null : state.manifestResources;
  }

  public void setInstalledManifestResourcesHash(@NotNull IDevice device, @NotNull String pkgName, HashCode hash) {
    getState(device, pkgName, true).manifestResources = hash;
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
    @Nullable public HashCode manifest;
    @Nullable public HashCode manifestResources;
  }
}
