/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.testutils.MockitoKt
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.StreamingTimeline
import com.android.tools.adtui.model.axis.ClampedAxisComponentModel
import com.android.tools.adtui.model.updater.Updater
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.memory.adapters.MemoryDataProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class LiveMemoryFootprintModelTest {
  private lateinit var myProfilers:StudioProfilers
  private lateinit var mockMemoryDataProvider: MemoryDataProvider
  private lateinit var myLiveMemoryFootprintModel: LiveMemoryFootprintModel

  @Before
  fun setUp() {
    myProfilers = Mockito.mock(StudioProfilers::class.java, Mockito.RETURNS_DEEP_STUBS)
    mockMemoryDataProvider = Mockito.mock(MemoryDataProvider::class.java, Mockito.RETURNS_DEEP_STUBS)
    val mockDetailedMemoryUsage = Mockito.mock(DetailedMemoryUsage::class.java, Mockito.RETURNS_DEEP_STUBS)
    MockitoKt.whenever(mockMemoryDataProvider.detailedMemoryUsage).thenReturn(mockDetailedMemoryUsage)
    MockitoKt.whenever(mockMemoryDataProvider.isLiveAllocationTrackingReady).thenReturn(true)
    MockitoKt.whenever(mockMemoryDataProvider.legends).thenReturn(MockitoKt.mock<MemoryStageLegends>())
    myLiveMemoryFootprintModel = LiveMemoryFootprintModel(myProfilers, mockMemoryDataProvider)
  }

  @Test
  fun testGetDetailedMemoryUsage() {
    val mockDetailedMemoryUsage = MockitoKt.mock<DetailedMemoryUsage>();
    MockitoKt.whenever(mockMemoryDataProvider.detailedMemoryUsage).thenReturn(mockDetailedMemoryUsage)
    myLiveMemoryFootprintModel = LiveMemoryFootprintModel(myProfilers, mockMemoryDataProvider)
    // Check if method returns expected DetailedMemoryUsage object
    val result = myLiveMemoryFootprintModel.detailedMemoryUsage
    assertThat(result).isEqualTo(mockDetailedMemoryUsage)
  }

  @Test
  fun testGetLegends() {
    val mockDetailedMemoryUsage = MockitoKt.mock<DetailedMemoryUsage>();
    val mockLegends = MockitoKt.mock<MemoryStageLegends>();
    MockitoKt.whenever(mockMemoryDataProvider.detailedMemoryUsage).thenReturn(mockDetailedMemoryUsage)
    MockitoKt.whenever(mockMemoryDataProvider.legends).thenReturn(mockLegends)
    MockitoKt.whenever(mockMemoryDataProvider.isLiveAllocationTrackingReady).thenReturn(true)
    myLiveMemoryFootprintModel = LiveMemoryFootprintModel(myProfilers, mockMemoryDataProvider)
    // Check if method returns expected MemoryStageLegends object
    val result = myLiveMemoryFootprintModel.legends
    assertThat(result).isEqualTo(mockLegends)
  }

  @Test
  fun testGetMemoryAxis() {
    val mockDetailedMemoryUsage = MockitoKt.mock<DetailedMemoryUsage>();
    val mockLegends = MockitoKt.mock<MemoryStageLegends>();
    val mockMemoryAxis = MockitoKt.mock<ClampedAxisComponentModel>();
    MockitoKt.whenever(mockMemoryDataProvider.detailedMemoryUsage).thenReturn(mockDetailedMemoryUsage)
    MockitoKt.whenever(mockMemoryDataProvider.legends).thenReturn(mockLegends)
    MockitoKt.whenever(mockMemoryDataProvider.isLiveAllocationTrackingReady).thenReturn(true)
    MockitoKt.whenever(mockMemoryDataProvider.memoryAxis).thenReturn(mockMemoryAxis)
    myLiveMemoryFootprintModel = LiveMemoryFootprintModel(myProfilers, mockMemoryDataProvider)
    // Check if method returns expected ClampedAxisComponentModel object
    val result = myLiveMemoryFootprintModel.memoryAxis
    assertThat(result).isEqualTo(mockMemoryAxis)
  }

  @Test
  fun testGetObjectsAxis() {
    val mockDetailedMemoryUsage = MockitoKt.mock<DetailedMemoryUsage>();
    val mockLegends = MockitoKt.mock<MemoryStageLegends>();
    val mockObjectAxis = MockitoKt.mock<ClampedAxisComponentModel>();
    MockitoKt.whenever(mockMemoryDataProvider.detailedMemoryUsage).thenReturn(mockDetailedMemoryUsage)
    MockitoKt.whenever(mockMemoryDataProvider.legends).thenReturn(mockLegends)
    MockitoKt.whenever(mockMemoryDataProvider.isLiveAllocationTrackingReady).thenReturn(true)
    MockitoKt.whenever(mockMemoryDataProvider.objectsAxis).thenReturn(mockObjectAxis)
    myLiveMemoryFootprintModel = LiveMemoryFootprintModel(myProfilers, mockMemoryDataProvider)
    // Check if method returns expected ClampedAxisComponentModel object
    val result = myLiveMemoryFootprintModel.objectAxis
    assertThat(result).isEqualTo(mockObjectAxis)
  }

  @Test
  fun testIsLiveAllocationTrackingReady() {
    val mockDetailedMemoryUsage = MockitoKt.mock<DetailedMemoryUsage>();
    val mockLegends = MockitoKt.mock<MemoryStageLegends>();
    MockitoKt.whenever(mockMemoryDataProvider.detailedMemoryUsage).thenReturn(mockDetailedMemoryUsage)
    MockitoKt.whenever(mockMemoryDataProvider.legends).thenReturn(mockLegends)
    MockitoKt.whenever(mockMemoryDataProvider.isLiveAllocationTrackingReady).thenReturn(true)
    myLiveMemoryFootprintModel = LiveMemoryFootprintModel(myProfilers, mockMemoryDataProvider)
    // Check if method returns true
    val result = myLiveMemoryFootprintModel.isLiveAllocationTrackingReady
    assertThat(result).isEqualTo(true)
  }

  @Test
  fun testGetRangeSelectionModel() {
    val mockDetailedMemoryUsage = MockitoKt.mock<DetailedMemoryUsage>();
    val mockLegends = MockitoKt.mock<MemoryStageLegends>();
    MockitoKt.whenever(mockMemoryDataProvider.detailedMemoryUsage).thenReturn(mockDetailedMemoryUsage)
    MockitoKt.whenever(mockMemoryDataProvider.legends).thenReturn(mockLegends)
    MockitoKt.whenever(mockMemoryDataProvider.isLiveAllocationTrackingReady).thenReturn(true)
    val mockTimeline = MockitoKt.mock<StreamingTimeline>()
    val mockDefaultTimeline = Mockito.mock(Range::class.java, Mockito.RETURNS_DEEP_STUBS)
    MockitoKt.whenever(myProfilers.timeline).thenReturn(mockTimeline)
    MockitoKt.whenever(mockTimeline.selectionRange).thenReturn(mockDefaultTimeline)
    MockitoKt.whenever(mockTimeline.viewRange).thenReturn(mockDefaultTimeline)
    myLiveMemoryFootprintModel = LiveMemoryFootprintModel(myProfilers, mockMemoryDataProvider)
    val result = myLiveMemoryFootprintModel.rangeSelectionModel
    assertThat(result).isNotNull()
  }

  @Test
  fun testEnter() {
    val mockDetailedMemoryUsage = MockitoKt.mock<DetailedMemoryUsage>();
    MockitoKt.whenever(mockMemoryDataProvider.detailedMemoryUsage).thenReturn(mockDetailedMemoryUsage)
    val mockUpdater = Mockito.mock(Updater::class.java, Mockito.RETURNS_DEEP_STUBS)
    MockitoKt.whenever(myProfilers.updater).thenReturn(mockUpdater)
    myLiveMemoryFootprintModel = LiveMemoryFootprintModel(myProfilers, mockMemoryDataProvider)
    myLiveMemoryFootprintModel.enter()
    Mockito.verify(mockUpdater, Mockito.times(1)).register(mockDetailedMemoryUsage)
  }

  @Test
  fun testExit() {
    val mockDetailedMemoryUsage = MockitoKt.mock<DetailedMemoryUsage>();
    MockitoKt.whenever(mockMemoryDataProvider.detailedMemoryUsage).thenReturn(mockDetailedMemoryUsage)
    val mockUpdater = Mockito.mock(Updater::class.java, Mockito.RETURNS_DEEP_STUBS)
    MockitoKt.whenever(myProfilers.updater).thenReturn(mockUpdater)
    myLiveMemoryFootprintModel = LiveMemoryFootprintModel(myProfilers, mockMemoryDataProvider)
    myLiveMemoryFootprintModel.exit()
    Mockito.verify(mockUpdater, Mockito.times(1)).unregister(mockDetailedMemoryUsage)
  }

  @Test
  fun testName() {
    val mockDetailedMemoryUsage = MockitoKt.mock<DetailedMemoryUsage>();
    MockitoKt.whenever(mockMemoryDataProvider.detailedMemoryUsage).thenReturn(mockDetailedMemoryUsage)
    val mockUpdater = Mockito.mock(Updater::class.java, Mockito.RETURNS_DEEP_STUBS)
    MockitoKt.whenever(myProfilers.updater).thenReturn(mockUpdater)
    myLiveMemoryFootprintModel = LiveMemoryFootprintModel(myProfilers, mockMemoryDataProvider)
    val result = myLiveMemoryFootprintModel.name
    assertThat(result).isEqualTo("LIVE_MEMORY")
  }
}