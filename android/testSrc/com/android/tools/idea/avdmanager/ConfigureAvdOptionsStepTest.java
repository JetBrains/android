/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.idea.avdmanager;

import junit.framework.TestCase;

import static com.android.sdklib.internal.avd.GpuMode.*;
import static com.android.sdklib.repository.targets.SystemImage.*;
import static com.android.tools.idea.avdmanager.ConfigureAvdOptionsStep.gpuOtherMode;
import static com.android.tools.idea.avdmanager.ConfigureAvdOptionsStep.isGoogleApiTag;
import static com.google.common.truth.Truth.assertThat;


public class ConfigureAvdOptionsStepTest extends TestCase {

  public void testIsGoogleApiTag() throws Exception {
    assertThat(isGoogleApiTag(GOOGLE_APIS_TAG)).isTrue();
    assertThat(isGoogleApiTag(TV_TAG)).isTrue();
    assertThat(isGoogleApiTag(WEAR_TAG)).isTrue();

    assertThat(isGoogleApiTag(DEFAULT_TAG)).isFalse();
    assertThat(isGoogleApiTag(GOOGLE_APIS_X86_TAG)).isFalse();
    assertThat(isGoogleApiTag(GLASS_TAG)).isFalse();
  }

  public void testGpuOtherMode() throws Exception {
    assertEquals(SWIFT, gpuOtherMode(23, true, true, true));
    assertEquals(SWIFT, gpuOtherMode(23, true, true, false));

    assertEquals(MESA, gpuOtherMode(22, false, true, false));
    assertEquals(MESA, gpuOtherMode(22, true, true, false));
    assertEquals(MESA, gpuOtherMode(22, true, false, false));
    assertEquals(MESA, gpuOtherMode(23, true, false, false));

    assertEquals(OFF, gpuOtherMode(22, true, true, true));
    assertEquals(OFF, gpuOtherMode(23, true, false, true));
    assertEquals(OFF, gpuOtherMode(23, false, false, true));
  }
}
