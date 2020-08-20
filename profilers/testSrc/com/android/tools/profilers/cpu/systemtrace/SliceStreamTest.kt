/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.adtui.model.Range
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SliceStreamTest {
  @Test
  fun testCallbackOnlyOnMatchingPattern() {
    val sliceList = buildSliceGroup(5)
    var callBackTimes = 0
    SliceStream(sliceList)
      .matchName("Test1")
      .enumerate { slice ->
        assertThat(slice.name).isEqualTo("Test1")
        callBackTimes++
        SliceStream.EnumerationResult.CONTINUE
      }
    assertThat(callBackTimes).isEqualTo(1)
  }

  @Test
  fun testCallbackOnlyOnMatchingTimeRange() {
    val sliceList = buildSliceGroup(5)
    val rangeList = listOf(Range(0.0, 1.0), Range(1.0, 2.0), Range(2.0, 3.0))
    var rangeIndex = 0
    SliceStream(sliceList)
      .overlapsRange(Range(1.0, 2.0))
      .enumerate { slice ->
        assertThat(slice.startTimestampUs).isEqualTo(rangeList[rangeIndex].min.toLong())
        assertThat(slice.endTimestampUs).isEqualTo(rangeList[rangeIndex].max.toLong())
        rangeIndex++
        SliceStream.EnumerationResult.CONTINUE
      }
    assertThat(rangeList).hasSize(rangeIndex)
  }

  @Test
  fun testCallbackFalseStopsEnumeration() {
    val sliceList = buildSliceGroup(5)
    var callBackTimes = 0
    SliceStream(sliceList).enumerate {
      callBackTimes++
      SliceStream.EnumerationResult.TERMINATE
    }
    assertThat(callBackTimes).isEqualTo(1)
  }

  @Test
  fun testEnumerationSkipsChildrenOnSkipChildren() {
    val sliceList = mutableListOf<TraceEventModel>()
    for (i in 0..5) {
      val list = mutableListOf<TraceEventModel>()
      for (j in 0..5) {
        list.add(TraceEventModel("Test", j.toLong(), j + 1L, 1L, emptyList()))
      }
      sliceList.add(TraceEventModel("Test", i.toLong(), i + 1L, 1L, list))
    }
    var callBackTimes = 0
    SliceStream(sliceList).enumerate {
      callBackTimes++
      SliceStream.EnumerationResult.SKIP_CHILDREN
    }
    assertThat(callBackTimes).isEqualTo(sliceList.size)
  }

  @Test
  fun testFindSliceReturnsOrNull() {
    val sliceList = buildSliceGroup(5)
    var slice = SliceStream(sliceList).matchName("Test1").findFirst()
    assertThat(slice).isNotNull()
    assertThat(slice!!.name).matches("Test1")
    slice = SliceStream(sliceList).matchName("Test1").overlapsRange(Range(4.0, Double.MAX_VALUE)).findFirst()
    assertThat(slice).isNull()
    slice = SliceStream(sliceList).matchName("FakeName").overlapsRange(Range(0.0, Double.MAX_VALUE)).findFirst()
    assertThat(slice).isNull()
    // Test substring case
    slice = SliceStream(sliceList).matchName("Test").overlapsRange(Range(0.0, Double.MAX_VALUE)).findFirst()
    assertThat(slice).isNull()
  }

  private fun buildSliceGroup(count: Int): MutableList<TraceEventModel> {
    val sliceList = mutableListOf<TraceEventModel>()
    for (i in 0..count) {
      sliceList.add(TraceEventModel("Test$i", i.toLong(), i + 1L, 1L, emptyList()))
    }
    return sliceList
  }
}