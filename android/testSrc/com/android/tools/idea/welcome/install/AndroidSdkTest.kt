/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.welcome.install

import com.android.prefs.AndroidLocationsSingleton
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.testutils.TestUtils
import com.android.tools.idea.avdmanager.AccelerationErrorCode
import com.android.tools.idea.avdmanager.CpuVendor
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.SystemInfo
import org.junit.Test

class AndroidSdkTest {
  @Test
  fun `get required SDK packages doesn't return emulator on Chrome OS`() {
    val sdk = AndroidSdk(true).apply {
      updateState(AndroidSdkHandler.getInstance(AndroidLocationsSingleton, TestUtils.getSdk()))
    }
    val packages = sdk.getRequiredSdkPackages(true)
    assertThat(packages).containsExactly("platform-tools")
  }

  @Test
  fun `Haxm is only compatible with Windows with Intel CPU`() {
    if (SystemInfo.isWindows && CpuVendor.isIntel) {
      assertThat(Haxm.InstallerInfo.checkInstallation()).isNotEqualTo(AccelerationErrorCode.HAXM_REQUIRES_WINDOWS);
    } else if (SystemInfo.isWindows && !CpuVendor.isIntel) {
      assertThat(Haxm.InstallerInfo.checkInstallation()).isEqualTo(AccelerationErrorCode.HAXM_REQUIRES_INTEL_CPU);
    } else {
      assertThat(Haxm.InstallerInfo.checkInstallation()).isEqualTo(AccelerationErrorCode.HAXM_REQUIRES_WINDOWS);
    }
  }

  @Test
  fun `AEHD is only compatible with Windows`() {
    if (SystemInfo.isWindows) {
      assertThat(Aehd.InstallerInfo.checkInstallation()).isNotEqualTo(AccelerationErrorCode.AEHD_REQUIRES_WINDOWS);
    } else {
      assertThat(Aehd.InstallerInfo.checkInstallation()).isEqualTo(AccelerationErrorCode.AEHD_REQUIRES_WINDOWS);
    }
  }
}
