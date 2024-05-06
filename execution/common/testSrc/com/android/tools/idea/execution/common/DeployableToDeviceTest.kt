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
package com.android.tools.idea.execution.common

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.util.UserDataHolder
import org.junit.Test
import org.mockito.Mockito.withSettings

class DeployableToDeviceTest {
  @Test
  fun testConfigurationThatDeploysToLocalDevice() {
    val configurationWithKey = mock<RunConfiguration>(withSettings().extraInterfaces(UserDataHolder::class.java))
    whenever((configurationWithKey as UserDataHolder).getUserData(DeployableToDevice.KEY)).thenReturn(true)

    assertThat(DeployableToDevice.deploysToLocalDevice(configurationWithKey)).isTrue()
  }

  @Test
  fun testConfigurationsThatDoNotDeployToLocalDevice() {
    val configurationWithoutKey = mock<RunConfiguration>()

    val configurationWithKeySetToFalse = mock<RunConfiguration>(withSettings().extraInterfaces(UserDataHolder::class.java))
    whenever((configurationWithKeySetToFalse as UserDataHolder).getUserData(DeployableToDevice.KEY)).thenReturn(false)

    val configurationWithKeyUnset = mock<RunConfiguration>(withSettings().extraInterfaces(UserDataHolder::class.java))
    whenever((configurationWithKeyUnset as UserDataHolder).getUserData(DeployableToDevice.KEY)).thenReturn(null)

    assertThat(DeployableToDevice.deploysToLocalDevice(configurationWithoutKey)).isFalse()
    assertThat(DeployableToDevice.deploysToLocalDevice(configurationWithKeySetToFalse)).isFalse()
    assertThat(DeployableToDevice.deploysToLocalDevice(configurationWithKeyUnset)).isFalse()
  }
}