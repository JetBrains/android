/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.sdk.AndroidSdks;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidSdkAdditionalData implements SdkAdditionalData {

  @NonNls private static final String BUILD_TARGET = "sdk";

  private final Sdk myAndroidSdk;

  // hash string
  private String myBuildTarget;

  private AndroidPlatform myAndroidPlatform = null;

  public AndroidSdkAdditionalData(@NotNull Sdk androidSdk) {
    myAndroidSdk = androidSdk;
  }

  public AndroidSdkAdditionalData(@NotNull Sdk androidSdk, @NotNull Element element) {
    myAndroidSdk = androidSdk;
    myBuildTarget = element.getAttributeValue(BUILD_TARGET);
  }

  public void setBuildTargetHashString(String targetHashString) {
    myBuildTarget = targetHashString;
    myAndroidPlatform = null;
  }

  public void setBuildTarget(IAndroidTarget target) {
    myBuildTarget = target != null ? target.hashString() : null;
    myAndroidPlatform = null;
  }

  public void save(Element element) {
    if (myBuildTarget != null) {
      element.setAttribute(BUILD_TARGET, myBuildTarget);
    }
  }

  @Nullable
  public IAndroidTarget getBuildTarget(@NotNull AndroidSdkData sdkData) {
    return myBuildTarget != null ? sdkData.findTargetByHashString(myBuildTarget) : null;
  }

  @Nullable
  public String getBuildTargetHashString() {
    return myBuildTarget;
  }

  @Nullable
  private static AndroidPlatform parse(@NotNull Sdk sdk) {
    if (!AndroidSdks.getInstance().isAndroidSdk(sdk)) {
      return null;
    }
    AndroidSdkData sdkData = AndroidSdkData.getSdkData(sdk);
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

  @Nullable
  public AndroidPlatform getAndroidPlatform() {
    if (myAndroidPlatform == null) {
      myAndroidPlatform = parse(myAndroidSdk);
    }
    return myAndroidPlatform;
  }

  @Nullable
  public static AndroidSdkAdditionalData from(@NotNull Sdk sdk) {
    SdkAdditionalData data = sdk.getSdkAdditionalData();
    return data instanceof AndroidSdkAdditionalData ? (AndroidSdkAdditionalData)data : null;
  }
}
