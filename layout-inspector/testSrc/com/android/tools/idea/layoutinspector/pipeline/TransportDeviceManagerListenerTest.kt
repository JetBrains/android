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
package com.android.tools.idea.layoutinspector.pipeline

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.TransportDeviceManagerListenerImpl
import com.android.tools.profiler.proto.Transport
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TransportDeviceManagerListenerTest {

  @Test
  fun flagIsEnabledByDefault() {
    val transportDeviceManagerListener = TransportDeviceManagerListenerImpl()
    val daemonConfig = Transport.DaemonConfig.newBuilder()
    transportDeviceManagerListener.customizeDaemonConfig(daemonConfig)

    assertThat(daemonConfig.hasLayoutInspectorConfig()).isTrue()
    assertThat(daemonConfig.layoutInspectorConfig.autoconnectEnabled).isTrue()
  }

  @Test
  fun daemonConfigReflectsFlagStatus() {
    runWithFlagState(true) {
      val transportDeviceManagerListener = TransportDeviceManagerListenerImpl()
      val daemonConfig = Transport.DaemonConfig.newBuilder()
      transportDeviceManagerListener.customizeDaemonConfig(daemonConfig)

      assertThat(daemonConfig.hasLayoutInspectorConfig()).isTrue()
      assertThat(daemonConfig.layoutInspectorConfig.autoconnectEnabled).isTrue()
    }

    runWithFlagState(false) {
      val transportDeviceManagerListener = TransportDeviceManagerListenerImpl()
      val daemonConfig = Transport.DaemonConfig.newBuilder()
      transportDeviceManagerListener.customizeDaemonConfig(daemonConfig)

      assertThat(daemonConfig.hasLayoutInspectorConfig()).isTrue()
      assertThat(daemonConfig.layoutInspectorConfig.autoconnectEnabled).isFalse()
    }
  }

  private fun runWithFlagState(desiredFlagState: Boolean, task: () -> Unit) {
    val flag = StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_AUTO_CONNECT_TO_FOREGROUND_PROCESS_ENABLED
    val flagPreviousState = flag.get()
    flag.override(desiredFlagState)

    task()

    // restore flag state
    flag.override(flagPreviousState)
  }
}