/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.post;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.truth.Truth;
import com.google.wireless.android.sdk.stats.GradleNativeAndroidModule;
import org.junit.Test;

public class ProjectStructureUsageTrackerSyncListenerUnitTest {
  @Test
  public void testStringToBuildSystemType() {
    Truth.assertThat(ProjectStructureUsageTrackerManager.stringToBuildSystemType("ndkBuild")).isEqualTo(
      GradleNativeAndroidModule.NativeBuildSystemType.NDK_BUILD);
    assertThat(ProjectStructureUsageTrackerManager.stringToBuildSystemType("cmake")).isEqualTo(
      GradleNativeAndroidModule.NativeBuildSystemType.CMAKE);
    assertThat(ProjectStructureUsageTrackerManager.stringToBuildSystemType("ndkCompile")).isEqualTo(
      GradleNativeAndroidModule.NativeBuildSystemType.NDK_COMPILE);
    assertThat(ProjectStructureUsageTrackerManager.stringToBuildSystemType("gradle")).isEqualTo(
      GradleNativeAndroidModule.NativeBuildSystemType.GRADLE_EXPERIMENTAL);
    assertThat(ProjectStructureUsageTrackerManager.stringToBuildSystemType("blaze")).isEqualTo(
      GradleNativeAndroidModule.NativeBuildSystemType.UNKNOWN_NATIVE_BUILD_SYSTEM_TYPE);
  }
}
