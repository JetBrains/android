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
package com.android.tools.profilers.memory

import com.android.tools.adtui.StatLabel
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE_NAME
import com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_PROCESS_NAME
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.memory.adapters.FakeCaptureObject
import com.android.tools.profilers.memory.adapters.FakeInstanceObject
import com.android.tools.profilers.memory.adapters.classifiers.AllHeapSet
import com.android.tools.profilers.memory.adapters.classifiers.HeapSet
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.swing.JComponent

class CapturePanelTest {

  private lateinit var profilers: StudioProfilers
  private lateinit var ideProfilerServices: FakeIdeProfilerServices
  private val myTimer = FakeTimer()
  private val transportService = FakeTransportService(myTimer)

  @Rule
  @JvmField
  val grpcChannel = FakeGrpcChannel("MemoryProfilerStageViewTestChannel", transportService)

  @get:Rule
  val applicationRule = ApplicationRule()

  @Before
  fun setupBase() {
    ideProfilerServices = FakeIdeProfilerServices()
    profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), ideProfilerServices, myTimer)
    profilers.setPreferredProcess(FAKE_DEVICE_NAME, FAKE_PROCESS_NAME, null)
  }

  @Test
  fun `panel shows numbers for selected heap`() {
    val capture = FakeCaptureObject.Builder().build()
    val heap1 = HeapSet(capture, "heap1", 1)
    val heap2 = HeapSet(capture, "heap2", 2)
    val allHeap = AllHeapSet(capture, arrayOf(heap1, heap2)).also { it.clearClassifierSets() }

    val insts1 = arrayOf(FakeInstanceObject.Builder(capture, 1, "obj").setHeapId(1).setShallowSize(4).build(),
                         FakeInstanceObject.Builder(capture, 2, "int").setHeapId(1).setShallowSize(8).build(),
                         FakeInstanceObject.Builder(capture, 3, "str").setHeapId(1).setShallowSize(14).build())
    val insts2 = arrayOf(FakeInstanceObject.Builder(capture, 4, "cat").setHeapId(2).setShallowSize(3).build(),
                         FakeInstanceObject.Builder(capture, 5, "dog").setHeapId(2).setShallowSize(5).build(),
                         FakeInstanceObject.Builder(capture, 6, "rat").setHeapId(2).setShallowSize(7).build())
    val insts = insts1 + insts2
    insts.forEach { allHeap.addDeltaInstanceObject(it) }


    val stage = MainMemoryProfilerStage(profilers, FakeCaptureObjectLoader())
    val selection = MemoryCaptureSelection(profilers.ideServices)
    val profilersView = StudioProfilersView(profilers, FakeIdeProfilerComponents())
    val panel = CapturePanel(profilersView, selection, null, profilers.timeline.selectionRange,
                             FakeIdeProfilerComponents(), profilers.timeline,true)

    selection.selectCaptureEntry(CaptureEntry(Any()) { capture })
    selection.finishSelectingCaptureObject(capture)

    selection.selectHeapSet(heap1)
    assertThat(panel.component.getStatLabelValue("Classes")).isEqualTo("${insts1.size}")
    assertThat(panel.component.getStatLabelValue("Shallow Size")).isEqualTo("${insts1.sumOf { it.shallowSize }}")

    selection.selectHeapSet(heap2)
    assertThat(panel.component.getStatLabelValue("Classes")).isEqualTo("${insts2.size}")
    assertThat(panel.component.getStatLabelValue("Shallow Size")).isEqualTo("${insts2.sumOf { it.shallowSize }}")

    selection.selectHeapSet(allHeap)
    assertThat(panel.component.getStatLabelValue("Classes")).isEqualTo("${insts.size}")
    assertThat(panel.component.getStatLabelValue("Shallow Size")).isEqualTo("${insts.sumOf { it.shallowSize }}")
  }

  companion object {
    fun JComponent.getStatLabelValue(desc: String): String? = TreeWalker(this).descendantStream()
      .filter { it is StatLabel && it.descText == desc}
      .map { (it as StatLabel).numText }
      .findFirst().orElse(null)
  }
}