/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.android.testutils.VirtualTimeScheduler
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo
import org.junit.Test
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.util.concurrent.TimeUnit

class InspectorClientLaunchMonitorTest {
  @Test
  fun monitorStopsStuckConnection() {
    val scheduler = VirtualTimeScheduler()
    run {
      val monitor = InspectorClientLaunchMonitor(scheduler)
      val client = mock<InspectorClient>()
      monitor.start(client)
      scheduler.advanceBy(CONNECT_TIMEOUT_SECONDS + 1, TimeUnit.SECONDS)
      verify(client).disconnect()
    }
    run {
      val monitor = InspectorClientLaunchMonitor(scheduler)
      val client = mock<InspectorClient>()
      monitor.start(client)
      scheduler.advanceBy(CONNECT_TIMEOUT_SECONDS - 1, TimeUnit.SECONDS)
      monitor.updateProgress(DynamicLayoutInspectorErrorInfo.AttachErrorState.START_REQUEST_SENT)
      scheduler.advanceBy(CONNECT_TIMEOUT_SECONDS - 1, TimeUnit.SECONDS)
      verify(client, times(0)).disconnect()
      scheduler.advanceBy(2, TimeUnit.SECONDS)
      verify(client).disconnect()
    }
    run {
      val monitor = InspectorClientLaunchMonitor(scheduler)
      val client = mock<InspectorClient>()
      monitor.updateProgress(CONNECTED_STATE)
      scheduler.advanceBy(CONNECT_TIMEOUT_SECONDS + 1, TimeUnit.SECONDS)
      verify(client, times(0)).disconnect()
    }
  }
}