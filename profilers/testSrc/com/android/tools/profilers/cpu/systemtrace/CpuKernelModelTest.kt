/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.profilers.cpu.systemtrace

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.Range
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.CpuProfilerStage
import com.android.tools.profilers.cpu.CpuProfilerTestUtils
import com.android.tools.profilers.cpu.FakeCpuService
import com.android.tools.profilers.cpu.MainProcessSelector
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.FakeMemoryService
import com.android.tools.profilers.network.FakeNetworkService
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

class CpuKernelModelTest {
  private val myTimer = FakeTimer()
  private lateinit var myCpuModel: CpuKernelModel
  private val myRange = Range()
  private lateinit var myStage: CpuProfilerStage

  @Rule
  @JvmField
  var myGrpcChannel = FakeGrpcChannel(
    "CpuKernelModelTest", FakeCpuService(), FakeTransportService(myTimer), FakeProfilerService(myTimer),
    FakeMemoryService(), FakeEventService(), FakeNetworkService.newBuilder().build()
  )

  @Before
  fun setup() {
    val services = FakeIdeProfilerServices()
    val profilers = StudioProfilers(ProfilerClient(myGrpcChannel.channel), services, myTimer)
    myStage = CpuProfilerStage(profilers)
    myCpuModel = CpuKernelModel(myRange, myStage)
  }

  @Test
  fun updateCaptureUpdatesModel() {
    assertThat(myCpuModel.isEmpty).isTrue()
    val parser = AtraceParser(MainProcessSelector(idHint = 1))
    val capture = parser.parse(CpuProfilerTestUtils.getTraceFile("atrace.ctrace"), 0)
    myStage.capture = capture;
    assertThat(myCpuModel.size).isEqualTo(4)
  }

  @Test
  fun fireContentsChanged() {
    var itemAddedCalled = 0
    var itemRemovedCalled = 0;
    var contentsChangedCalled = 0;
    myCpuModel.addListDataListener(object : ListDataListener {
      override fun intervalAdded(e: ListDataEvent) {
        itemAddedCalled++
      }

      override fun intervalRemoved(e: ListDataEvent) {
        itemRemovedCalled++
      }

      override fun contentsChanged(e: ListDataEvent) {
        contentsChangedCalled++
      }
    })

    // Test no capture results in no events being triggered
    assertThat(contentsChangedCalled).isEqualTo(0)
    assertThat(itemAddedCalled).isEqualTo(0)
    assertThat(itemRemovedCalled).isEqualTo(0)

    val parser = AtraceParser(MainProcessSelector(idHint = 1))
    val capture = parser.parse(CpuProfilerTestUtils.getTraceFile("atrace.ctrace"), 0)
    myStage.capture = capture;

    assertThat(contentsChangedCalled).isEqualTo(1)
    assertThat(itemAddedCalled).isEqualTo(4)
    assertThat(itemRemovedCalled).isEqualTo(0)

  }
}
