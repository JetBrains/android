/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.android.sdk;

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.sdk.AndroidSdks;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.roots.ModuleRootManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidPlatform {
  @NotNull private final AndroidSdkData mySdkData;
  @NotNull private final IAndroidTarget myTarget;

  public AndroidPlatform(@NotNull AndroidSdkData sdkData, @NotNull IAndroidTarget target) {
    mySdkData = sdkData;
    myTarget = target;
  }

  @Nullable
  public static AndroidPlatform getInstance(@NotNull Module module) {
    if (module.isDisposed()) {
      return null;
    }
    Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    return sdk != null ? getInstance(sdk) : null;
  }

  @Nullable
  public static AndroidPlatform getInstance(@NotNull Sdk sdk) {
    AndroidSdkAdditionalData data = AndroidSdks.getInstance().getAndroidSdkAdditionalData(sdk);
    return data != null ? data.getAndroidPlatform() : null;
  }

  @NotNull
  public AndroidSdkData getSdkData() {
    return mySdkData;
  }

  @NotNull
  public IAndroidTarget getTarget() {
    return myTarget;
  }

  @Nullable
  public static AndroidPlatform parse(@NotNull Sdk sdk) {
    if (!AndroidSdks.getInstance().isAndroidSdk(sdk)) {
      return null;
    }
    AndroidSdkData sdkData = StudioAndroidSdkData.getSdkData(sdk);
    if (sdkData != null) {
      SdkAdditionalData data = sdk.getSdkAdditionalData();
      if (data instanceof AndroidSdkAdditionalData) {
        IAndroidTarget target = ((AndroidSdkAdditionalData)data).getBuildTarget(sdkData);
        if (target != null) {
          return new AndroidPlatform(sdkData, target);
        }
      }
    }
    return null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AndroidPlatform platform = (AndroidPlatform)o;

    if (!mySdkData.equals(platform.mySdkData)) return false;
    if (!myTarget.equals(platform.myTarget)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = mySdkData.hashCode();
    result = 31 * result + myTarget.hashCode();
    return result;
  }

  public boolean needToAddAnnotationsJarToClasspath() {
    return AndroidSdks.getInstance().needsAnnotationsJarInClasspath(myTarget);
  }

  public int getApiLevel() {
    return myTarget.getVersion().getApiLevel();
  }

  @NotNull
  public AndroidVersion getApiVersion() {
    return myTarget.getVersion();
  }
}
