/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.sdk;

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import org.jetbrains.annotations.NotNull;

public class AndroidPlatform {
  @NotNull private final AndroidSdkData mySdkData;
  @NotNull private final IAndroidTarget myTarget;

  public AndroidPlatform(@NotNull AndroidSdkData sdkData, @NotNull IAndroidTarget target) {
    mySdkData = sdkData;
    myTarget = target;
  }

  @NotNull
  public AndroidSdkData getSdkData() {
    return mySdkData;
  }

  @NotNull
  public IAndroidTarget getTarget() {
    return myTarget;
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
    return Annotations.needsAnnotationsJarInClasspath(myTarget);
  }

  public int getApiLevel() {
    return myTarget.getVersion().getApiLevel();
  }

  @NotNull
  public AndroidVersion getApiVersion() {
    return myTarget.getVersion();
  }
}
