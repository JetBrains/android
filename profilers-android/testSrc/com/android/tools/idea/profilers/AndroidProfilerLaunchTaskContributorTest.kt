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
package com.android.tools.idea.profilers

import com.android.ddmlib.IDevice
import com.android.ddmlib.internal.DeviceImpl
import com.android.sdklib.AndroidVersion
import com.android.testutils.MockitoKt
import com.android.tools.idea.run.editor.ProfilerState
import com.android.tools.idea.transport.TransportFileManager
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.testFramework.ProjectRule
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

class AndroidProfilerLaunchTaskContributorTest {
  @get:Rule
  val projectRule = ProjectRule()

  @Test
  fun testIsProfilerLaunch() {
    // Should return true for the legacy profile executor.
    val profileExecutor = ProfileRunExecutor.getInstance()!!
    assertThat(AndroidProfilerLaunchTaskContributor.isProfilerLaunch(profileExecutor)).isTrue()

    // Should return true for the profileable executor group.
    val profileableExecutor = ProfileRunExecutorGroup.getInstance()!!.childExecutors()[0]
    assertThat(AndroidProfilerLaunchTaskContributor.isProfilerLaunch(profileableExecutor)).isTrue()

    // Should return false otherwise.
    val defaultRunExecutor = DefaultRunExecutor.getRunExecutorInstance()
    assertThat(AndroidProfilerLaunchTaskContributor.isProfilerLaunch(defaultRunExecutor)).isFalse()
  }

  @Test
  fun testEmptyAmStartOptions() {
    val device = DeviceImpl(null, "123", IDevice.DeviceState.ONLINE)
    val profilerState = ProfilerState()

    // Empty string for non-profiler executors.
    val defaultRunExecutor = DefaultRunExecutor.getRunExecutorInstance()
    assertThat(AndroidProfilerLaunchTaskContributor.getAmStartOptions(projectRule.project, "app", profilerState, device,
                                                                      defaultRunExecutor)).isEmpty()

    // Empty string for null ProfilerState.
    val profileExecutor = ProfileRunExecutor.getInstance()!!
    assertThat(AndroidProfilerLaunchTaskContributor.getAmStartOptions(projectRule.project, "app", null, device, profileExecutor)).isEmpty()
  }

  @Test
  fun testAgentConfigIsEmptyForProfileable() {
    val device = Mockito.mock(IDevice::class.java)
    MockitoKt.whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.O_MR1))
    val fileManager = TransportFileManager(device)

    val result = fileManager.configureStartupAgent("com.example.app", "foo", ProfileRunExecutorGroup.getInstance()!!.childExecutors()[0].id)
    assertThat(result).isEmpty()
  }
}