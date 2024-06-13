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
package com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection

import com.android.tools.idea.layoutinspector.runningdevices.withAutoConnect
import com.android.tools.profiler.proto.Transport
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import org.junit.Rule
import org.junit.Test

class LayoutInspectorTransportConfigContributorTest {
  @get:Rule val applicationRule = ApplicationRule()

  @Test
  fun flagIsEnabledByDefault() {
    val transportFlagController = LayoutInspectorTransportConfigContributor()
    val daemonConfig = Transport.DaemonConfig.newBuilder()
    transportFlagController.customizeDaemonConfig(daemonConfig)

    assertThat(daemonConfig.hasLayoutInspectorConfig()).isTrue()
    assertThat(daemonConfig.layoutInspectorConfig.autoconnectEnabled).isTrue()
  }

  @Test
  fun daemonConfigReflectsFlagStatus() = withAutoConnect {
    val transportFlagController = LayoutInspectorTransportConfigContributor()
    val daemonConfig = Transport.DaemonConfig.newBuilder()

    enableAutoConnect = true
    transportFlagController.customizeDaemonConfig(daemonConfig)

    assertThat(daemonConfig.hasLayoutInspectorConfig()).isTrue()
    assertThat(daemonConfig.layoutInspectorConfig.autoconnectEnabled).isTrue()

    enableAutoConnect = false
    transportFlagController.customizeDaemonConfig(daemonConfig)

    assertThat(daemonConfig.hasLayoutInspectorConfig()).isTrue()
    assertThat(daemonConfig.layoutInspectorConfig.autoconnectEnabled).isFalse()
  }
}
