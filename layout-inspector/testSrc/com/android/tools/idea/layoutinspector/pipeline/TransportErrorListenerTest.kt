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

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.appinspection.internal.process.toDeviceDescriptor
import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.android.tools.idea.layoutinspector.metrics.LayoutInspectorMetrics
import com.android.tools.idea.layoutinspector.ui.InspectorBannerService
import com.android.tools.profiler.proto.Common
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorTransportError
import com.intellij.testFramework.ProjectRule
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.verify

class TransportErrorListenerTest {

  private val device1 = Common.Device.newBuilder()
    .setDeviceId(1)
    .setManufacturer("man1")
    .setModel("mod1")
    .setSerial("serial1")
    .setIsEmulator(false)
    .setApiLevel(1)
    .setVersion("version1")
    .setCodename("codename1")
    .setState(Common.Device.State.ONLINE)
    .build()

  @get:Rule
  val projectRule = ProjectRule()

  @Test
  fun testErrorShowsBanner() {
    val bannerService = InspectorBannerService.getInstance(projectRule.project) ?: error("no banner")
    val mockMetrics = mock<LayoutInspectorMetrics>()
    val transportErrorListener = TransportErrorListener(projectRule.project, mockMetrics)

    transportErrorListener.onStartTransportDaemonServerFail(device1, mock())

    val notification1 = bannerService.notifications.single()
    assertThat(notification1.message).isEqualTo(LayoutInspectorBundle.message("two.versions.of.studio.running"))
    assertThat(notification1.actions).isEmpty()
    verify(mockMetrics).logTransportError(
      DynamicLayoutInspectorTransportError.Type.TRANSPORT_FAILED_TO_START_DAEMON,
      device1.toDeviceDescriptor()
    )

    transportErrorListener.onPreTransportDaemonStart(mock())

    assertThat(bannerService.notifications).isEmpty()
  }
}