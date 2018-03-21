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
package com.android.tools.profilers.cpu.atrace

import com.android.tools.adtui.model.Range
import com.android.tools.profilers.cpu.atrace.AtraceTestUtils.Companion.DELTA
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import trebuchet.model.base.SliceGroup
import trebuchet.model.fragments.SliceGroupBuilder

class SliceStreamTest {
  @Test
  fun testCallbackOnlyOnMatchingPattern() {
    val sliceList = buildSliceGroup(5)
    var callBackTimes = 0;
    SliceStream(sliceList)
      .matchName("Test1")
      .enumerate(
          { slice ->
            assertThat(slice.name).isEqualTo("Test1")
            callBackTimes++
            true
          }
      )
    assertThat(callBackTimes).isEqualTo(1)
  }

  @Test
  fun testCallbackOnlyOnMatchingTimeRange() {
    val sliceList = buildSliceGroup(5)
    val rangeList = listOf<Range>(Range(0.0, 1.0), Range(1.0, 2.0), Range(2.0, 3.0))
    var rangeIndex = 0;
    SliceStream(sliceList)
      .overlapsRange(Range(1.0, 2.0))
      .enumerate(
          { slice ->
            assertThat(slice.startTime).isWithin(DELTA).of(rangeList[rangeIndex].min)
            assertThat(slice.endTime).isWithin(DELTA).of(rangeList[rangeIndex].max)
            rangeIndex++
            true
          }
      )
    assertThat(rangeList).hasSize(rangeIndex)
  }

  @Test
  fun testCallbackFalseStopsEnumeration() {
    val sliceList = buildSliceGroup(5)
    var callBackTimes = 0;
    SliceStream(sliceList).enumerate(
        { slice ->
          callBackTimes++
          false
        }
    )
    assertThat(callBackTimes).isEqualTo(1)
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

  fun buildSliceGroup(count: Int): MutableList<SliceGroup> {
    val sliceList = mutableListOf<SliceGroup>()
    for (i in 0..count) {
      sliceList.add(SliceGroupBuilder.MutableSliceGroup(i * 1.0, (i + 1.0), false, 0.0, "Test" + i, mutableListOf()))
    }
    return sliceList
  }
}