/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.profilers.cpu

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.SeriesData
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.google.common.collect.Iterables
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ImportedTraceThreadDataSeriesTest {

  private lateinit var myCapture: CpuCapture

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel("ImportedTraceThreadDataSeriesTest", FakeCpuService())

  private var mySeries: ImportedTraceThreadDataSeries? = null

  @Before
  fun setUp() {
    val timer = FakeTimer()
    val ideServices = FakeIdeProfilerServices()
    val profilers = StudioProfilers(ProfilerClient(myGrpcChannel.channel), ideServices, timer)
    // One second must be enough for new devices (and processes) to be picked up
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)

    val stage = CpuProfilerStage(profilers, CpuProfilerTestUtils.getTraceFile("valid_trace.trace"))
    stage.enter()
    myCapture = stage.capture!!
    val mainThreadId = 516
    mySeries = ImportedTraceThreadDataSeries(myCapture, mainThreadId)
  }

  @Test
  fun emptyRangeShouldReturnEmptySeries() {
    val empty = Range()
    val dataSeries = mySeries!!.getDataForRange(empty)
    assertThat(dataSeries).isNotNull()
    // No data within given range
    assertThat(dataSeries).isEmpty()
  }

  @Test
  fun fullRangeShouldReturnAllStates() {
    val fullRange = Range(-java.lang.Double.MAX_VALUE, java.lang.Double.MAX_VALUE)
    val dataSeries = mySeries!!.getDataForRange(fullRange)
    assertThat(dataSeries).isNotEmpty()

    // Imported ranges have pairs of HAS_ACTIVITY and NO_ACTIVITY states. We add one pair for each child of the root node for the thread.
    var hasActivityCount = 0
    var noActivityCount = 0
    var othersActivity = 0

    for (state in dataSeries) {
      if (state.value == ThreadState.HAS_ACTIVITY) {
        hasActivityCount++
      }
      else if (state.value == ThreadState.NO_ACTIVITY) {
        noActivityCount++
      }
      else {
        othersActivity++
      }
    }

    assertThat(hasActivityCount).isEqualTo(noActivityCount)
    assertThat(othersActivity).isEqualTo(0)

    val rootChildren = myCapture.getCaptureNode(myCapture.mainThreadId)!!.children
    // Check that we have one pair per children.
    assertThat(hasActivityCount).isEqualTo(rootChildren.size)
  }

  @Test
  fun rangeAfterStatesReturnsOnlyLastState() {
    val lastChild = Iterables.getLast(myCapture.getCaptureNode(myCapture.mainThreadId)!!.children)
    val rangeAfterStates = Range((lastChild.end + 1).toDouble(), java.lang.Double.MAX_VALUE)
    val dataSeries = mySeries!!.getDataForRange(rangeAfterStates)
    // Assert that we return only the last NO_ACTIVITY state
    assertThat(dataSeries).containsExactly(SeriesData(lastChild.end, ThreadState.NO_ACTIVITY))
  }

  @Test
  fun rangeBeforeStatesReturnsOnlyFirstState() {
    val firstChild = myCapture.getCaptureNode(myCapture.mainThreadId)!!.children[0]
    val rangeBeforeStates = Range(-java.lang.Double.MAX_VALUE, (firstChild.start - 1).toDouble())
    val dataSeries = mySeries!!.getDataForRange(rangeBeforeStates)
    // Assert that we return only the first HAS_ACTIVITY state
    assertThat(dataSeries).containsExactly(SeriesData(firstChild.start, ThreadState.HAS_ACTIVITY))
  }
}
