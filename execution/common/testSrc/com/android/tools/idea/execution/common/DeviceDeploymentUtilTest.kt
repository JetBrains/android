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
import com.intellij.execution.configurations.RunConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.Mockito.withSettings

class DeviceDeploymentUtilTest {
  @Test
  fun testConfigurationThatDeployToLocalDevice() {
    val configurationThatDeployToLocalDevice = mock<RunConfiguration>(withSettings().extraInterfaces(DeployableToDevice::class.java))
    whenever((configurationThatDeployToLocalDevice as DeployableToDevice).deploysToLocalDevice()).thenReturn(true)

    assertThat(DeviceDeploymentUtil.deploysToLocalDevice(configurationThatDeployToLocalDevice)).isTrue()
  }

  @Test
  fun testConfigurationsThatDoNotDeployToLocalDevice() {
    val configurationThatDoesNotInheritDeployableToDevice = mock<RunConfiguration>()
    val configurationThatDoesNotDeployToLocalDevice = mock<RunConfiguration>(withSettings().extraInterfaces(DeployableToDevice::class.java))
    whenever((configurationThatDoesNotDeployToLocalDevice as DeployableToDevice).deploysToLocalDevice()).thenReturn(false)

    assertThat(DeviceDeploymentUtil.deploysToLocalDevice(configurationThatDoesNotInheritDeployableToDevice)).isFalse()
    assertThat(DeviceDeploymentUtil.deploysToLocalDevice(configurationThatDoesNotDeployToLocalDevice)).isFalse()
  }
}