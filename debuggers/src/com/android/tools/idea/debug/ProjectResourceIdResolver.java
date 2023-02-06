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
package com.android.tools.idea.debug;

import com.android.sdklib.IAndroidTarget;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.StudioAndroidSdkData;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ProjectResourceIdResolver implements ResourceIdResolver {
  private final Project myProject;

  private Int2ObjectMap<String> myIdMap;
  private boolean myInitialized;

  @NotNull
  public static ResourceIdResolver getInstance(@NotNull Project project) {
    return project.getService(ResourceIdResolver.class);
  }

  private ProjectResourceIdResolver(@NotNull Project project) {
    myProject = project;
  }

  /** Returns the resource name corresponding to a given id if the id is present in the Android framework's exported ids (in public.xml) */
  @Override
  @Nullable
  public String getAndroidResourceName(int resId) {
    if (!myInitialized) {
      myIdMap = getIdMap();
      myInitialized = true;
    }

    return myIdMap == null ? null : myIdMap.get(resId);
  }

  private Int2ObjectMap<String> getIdMap() {
    AndroidFacet facet = null;
    for (Module m : ModuleManager.getInstance(myProject).getModules()) {
      facet = AndroidFacet.getInstance(m);
      if (facet != null) {
        break;
      }
    }

    AndroidSdkData sdkData = facet == null ? null : StudioAndroidSdkData.getSdkData(facet);
    if (sdkData == null) {
      return null;
    }

    IAndroidTarget[] targets = sdkData.getTargets();
    if (targets.length == 0) {
      return null;
    }

    return AndroidTargetData.get(sdkData, targets[targets.length - 1]).getPublicIdMap();
  }
}
