/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.sdk;

import com.android.sdklib.IAndroidTarget;
import com.android.tools.sdk.AndroidSdkData;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.File;

/** Compat class for {@link com.android.tools.sdk.AndroidSdkData} */
public class AndroidSdkDataCompat {
  AndroidSdkData androidSdkData;

  private AndroidSdkDataCompat(File sdkLocation, boolean forceReparse) {
    androidSdkData = AndroidSdkData.getSdkData(sdkLocation, forceReparse);
  }

  public static AndroidSdkDataCompat getSdkData(String sdkHomepath) {
    return new AndroidSdkDataCompat(new File(sdkHomepath), false);
  }

  @CanIgnoreReturnValue
  public static AndroidSdkDataCompat getSdkData(File sdkLocation, boolean forceReparse) {
    return new AndroidSdkDataCompat(sdkLocation, forceReparse);
  }

  public IAndroidTarget[] getTargets() {
    return androidSdkData.getTargets();
  }
}
