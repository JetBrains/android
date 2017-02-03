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
package com.android.tools.idea.testing;

import com.android.sdklib.IAndroidTarget;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Arrays;
import java.util.Optional;

import static com.android.testutils.TestUtils.getLatestAndroidPlatform;
import static com.google.common.truth.Truth.assertThat;
import static org.jetbrains.android.sdk.AndroidSdkData.getSdkData;
import static org.junit.Assert.assertNotNull;

public final class Sdks {
  private Sdks() {
  }

  @NotNull
  public static IAndroidTarget findLatestAndroidTarget(@NotNull File sdkPath) {
    AndroidSdkData sdkData = getSdkData(sdkPath);
    assertNotNull(sdkData);
    IAndroidTarget[] targets = sdkData.getTargets(false /* do not include add-ons */);
    assertThat(targets).isNotEmpty();

    // Use the latest platform, which is checked-in as a full SDK. Older platforms may not be checked in full, to save space.
    Optional<IAndroidTarget> found =
      Arrays.stream(targets).filter(target -> target.hashString().equals(getLatestAndroidPlatform())).findFirst();

    IAndroidTarget target = found.isPresent() ? found.get() : null;
    assertNotNull(target);
    return target;
  }

}
