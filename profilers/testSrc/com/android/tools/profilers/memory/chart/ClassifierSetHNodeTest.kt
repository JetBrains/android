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
package com.android.tools.profilers.memory.chart

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.memory.FakeCaptureObjectLoader
import com.android.tools.profilers.memory.MemoryCaptureObjectTestUtils
import com.android.tools.profilers.memory.MainMemoryProfilerStage
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ClassifierSetHNodeTest {
  private val myTimer = FakeTimer()

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel("MEMORY_TEST_CHANNEL", FakeTransportService(myTimer))
  private lateinit var myStage: MainMemoryProfilerStage

  @Before
  fun before() {
    val loader = FakeCaptureObjectLoader()
    loader.setReturnImmediateFuture(true)
    val fakeIdeProfilerServices = FakeIdeProfilerServices()
    myStage = MainMemoryProfilerStage(
      StudioProfilers(ProfilerClient(myGrpcChannel.channel), fakeIdeProfilerServices, FakeTimer()),
      loader)
  }

  @Test
  fun childrenOrderedBySize() {
    val heapSet = MemoryCaptureObjectTestUtils.createAndSelectHeapSet(myStage)
    val model = MemoryVisualizationModel()
    model.axisFilter = MemoryVisualizationModel.XAxisFilter.TOTAL_COUNT
    val node = ClassifierSetHNode(model, heapSet, 0)
    assertThat(node.childCount).isEqualTo(3)
    assertThat(node.firstChild).isEqualTo(node.getChildAt(0))
    assertThat(node.lastChild).isEqualTo(node.getChildAt(2))
    var previousDuration = node.getChildAt(0).duration
    for (i in 0 until node.childCount) {
      val child = node.getChildAt(i)
      assertThat(child.duration).isAtMost(previousDuration)
      previousDuration = child.duration
    }
  }

  @Test
  fun updateChildOffsetsOrdersChildByStartTime() {
    val heapSet = MemoryCaptureObjectTestUtils.createAndSelectHeapSet(myStage)
    val model = MemoryVisualizationModel()
    model.axisFilter = MemoryVisualizationModel.XAxisFilter.TOTAL_SIZE
    val node = ClassifierSetHNode(model, heapSet, 0)
    node.updateChildrenOffsets()
    var expectedStart = 0L
    for (i in 0 until node.childCount) {
      val child = node.getChildAt(i)
      assertThat(child.start).isEqualTo(expectedStart)
      expectedStart = child.end
    }
  }

  @Test
  fun durationRespectsFilter() {
    val heapSet = MemoryCaptureObjectTestUtils.createAndSelectHeapSet(myStage)
    val model = MemoryVisualizationModel()
    model.axisFilter = MemoryVisualizationModel.XAxisFilter.TOTAL_COUNT
    val root = ClassifierSetHNode(model, heapSet, 0)
    assertThat(heapSet.totalObjectCount).isEqualTo(root.duration)
    model.axisFilter = MemoryVisualizationModel.XAxisFilter.TOTAL_SIZE
    assertThat(heapSet.totalShallowSize).isEqualTo(root.duration)
    model.axisFilter = MemoryVisualizationModel.XAxisFilter.ALLOC_COUNT
    assertThat(heapSet.deltaAllocationCount).isEqualTo(root.duration)
    model.axisFilter = MemoryVisualizationModel.XAxisFilter.ALLOC_SIZE
    assertThat(heapSet.totalRemainingSize).isEqualTo(root.duration)
  }
}