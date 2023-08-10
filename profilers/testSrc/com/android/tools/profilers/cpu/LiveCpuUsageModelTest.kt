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
package com.android.tools.profilers.cpu

import com.android.testutils.MockitoKt
import com.android.tools.adtui.model.AspectModel
import com.android.tools.adtui.model.DurationDataModel
import com.android.tools.adtui.model.RangeSelectionModel
import com.android.tools.adtui.model.axis.AxisComponentModel
import com.android.tools.adtui.model.updater.UpdatableManager
import com.android.tools.adtui.model.updater.Updater
import com.android.tools.profilers.StreamingStage
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.adapters.CpuDataProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class LiveCpuUsageModelTest {
  private lateinit var myProfilers:StudioProfilers
  private lateinit var mockCpuDataProvider: CpuDataProvider
  private lateinit var myLiveCpuUsageModel: LiveCpuUsageModel
  private lateinit var stage: StreamingStage

  @Before
  fun setUp() {
    myProfilers = Mockito.mock(StudioProfilers::class.java, Mockito.RETURNS_DEEP_STUBS)
    mockCpuDataProvider = Mockito.mock(CpuDataProvider::class.java, Mockito.RETURNS_DEEP_STUBS)
    stage = Mockito.mock(StreamingStage::class.java, Mockito.RETURNS_DEEP_STUBS)
    val mockDetailedCpuUsage = Mockito.mock(DetailedCpuUsage::class.java, Mockito.RETURNS_DEEP_STUBS)
    val mockLegendsCpu = Mockito.mock(CpuProfilerStage.CpuStageLegends::class.java, Mockito.RETURNS_DEEP_STUBS)
    val mockRangeSelectionModel = Mockito.mock(RangeSelectionModel::class.java, Mockito.RETURNS_DEEP_STUBS)

    MockitoKt.whenever(mockCpuDataProvider.cpuUsage).thenReturn(mockDetailedCpuUsage)
    MockitoKt.whenever(mockCpuDataProvider.legends).thenReturn(mockLegendsCpu)
    MockitoKt.whenever(mockCpuDataProvider.rangeSelectionModel).thenReturn(mockRangeSelectionModel)
    myLiveCpuUsageModel = LiveCpuUsageModel(myProfilers, mockCpuDataProvider, stage)
  }

  @Test
  fun testGetAspect() {
    val mockAspectModel = MockitoKt.mock<AspectModel<CpuProfilerAspect>>()
    MockitoKt.whenever(mockCpuDataProvider.aspect).thenReturn(mockAspectModel)
    // Check if method returns expected aspect model object
    val result = myLiveCpuUsageModel.aspect
    assertThat(result).isEqualTo(mockAspectModel)
  }

  @Test
  fun testGetRangeSelectionModel() {
    val mockRangeSelectionModel = MockitoKt.mock<RangeSelectionModel>()
    MockitoKt.whenever(mockCpuDataProvider.rangeSelectionModel).thenReturn(mockRangeSelectionModel)
    // Check if method returns expected range selection model object
    val result = myLiveCpuUsageModel.rangeSelectionModel
    assertThat(result).isEqualTo(mockRangeSelectionModel)
  }

  @Test
  fun testGetUpdatableManager() {
    val mockUpdatableManager = MockitoKt.mock<UpdatableManager>()
    MockitoKt.whenever(mockCpuDataProvider.updatableManager).thenReturn(mockUpdatableManager)
    // Check if method returns expected updatable manager object
    val result = myLiveCpuUsageModel.updatableManager
    assertThat(result).isEqualTo(mockUpdatableManager)
  }

  @Test
  fun testGetCpuThreadStates() {
    val mockCpuThreadsStates = MockitoKt.mock<CpuThreadsModel>()
    MockitoKt.whenever(mockCpuDataProvider.threadStates).thenReturn(mockCpuThreadsStates)
    // Check if method returns expected thread states object
    val result = myLiveCpuUsageModel.threadStates
    assertThat(result).isEqualTo(mockCpuThreadsStates)
  }

  @Test
  fun testGetCpuUsageAxis() {
    val mockCpuUsageAxis = MockitoKt.mock<AxisComponentModel>()
    MockitoKt.whenever(mockCpuDataProvider.cpuUsageAxis).thenReturn(mockCpuUsageAxis)
    // Check if method returns expected usage axis object
    val result = myLiveCpuUsageModel.cpuUsageAxis
    assertThat(result).isEqualTo(mockCpuUsageAxis)
  }

  @Test
  fun testGetThreadCountAxis() {
    val mockThreadCountAxis = MockitoKt.mock<AxisComponentModel>()
    MockitoKt.whenever(mockCpuDataProvider.threadCountAxis).thenReturn(mockThreadCountAxis)
    // Check if method returns expected count axis object
    val result = myLiveCpuUsageModel.threadCountAxis
    assertThat(result).isEqualTo(mockThreadCountAxis)
  }

  @Test
  fun testGetTimeAxisGuide() {
    val mockTimeAxisGuide = MockitoKt.mock<AxisComponentModel>()
    MockitoKt.whenever(mockCpuDataProvider.timeAxisGuide).thenReturn(mockTimeAxisGuide)
    // Check if method returns expected time axis guide object
    val result = myLiveCpuUsageModel.timeAxisGuide
    assertThat(result).isEqualTo(mockTimeAxisGuide)
  }

  @Test
  fun testGetCpuUsage() {
    val mockDetailedCpuUsage = MockitoKt.mock<DetailedCpuUsage>()
    MockitoKt.whenever(mockCpuDataProvider.cpuUsage).thenReturn(mockDetailedCpuUsage)
    // Check if method returns expected cpu usage object
    val result = myLiveCpuUsageModel.cpuUsage
    assertThat(result).isEqualTo(mockDetailedCpuUsage)
  }

  @Test
  fun testGetCpuStageLegends() {
    val mockCpuStageLegends = MockitoKt.mock<CpuProfilerStage.CpuStageLegends>()
    MockitoKt.whenever(mockCpuDataProvider.legends).thenReturn(mockCpuStageLegends)
    // Check if method returns expected cpu stage legends object
    val result = myLiveCpuUsageModel.legends
    assertThat(result).isEqualTo(mockCpuStageLegends)
  }

  @Test
  fun testGetTraceDurations() {
    val mockDurationDataModel = MockitoKt.mock<DurationDataModel<CpuTraceInfo>>()
    MockitoKt.whenever(mockCpuDataProvider.traceDurations).thenReturn(mockDurationDataModel)
    // Check if method returns expected duration data model object
    val result = myLiveCpuUsageModel.traceDurations
    assertThat(result).isEqualTo(mockDurationDataModel)
  }

  @Test
  fun testEnter() {
    val mockDetailedCpuUsage = MockitoKt.mock<DetailedCpuUsage>();
    MockitoKt.whenever(mockCpuDataProvider.cpuUsage).thenReturn(mockDetailedCpuUsage)
    val mockLegendsCpu = Mockito.mock(CpuProfilerStage.CpuStageLegends::class.java, Mockito.RETURNS_DEEP_STUBS)
    val mockRangeSelectionModel = Mockito.mock(RangeSelectionModel::class.java, Mockito.RETURNS_DEEP_STUBS)
    MockitoKt.whenever(mockCpuDataProvider.legends).thenReturn(mockLegendsCpu)
    MockitoKt.whenever(mockCpuDataProvider.rangeSelectionModel).thenReturn(mockRangeSelectionModel)
    myLiveCpuUsageModel = LiveCpuUsageModel(myProfilers, mockCpuDataProvider, stage)
    val mockUpdater = Mockito.mock(Updater::class.java, Mockito.RETURNS_DEEP_STUBS)
    MockitoKt.whenever(myProfilers.updater).thenReturn(mockUpdater)
    myLiveCpuUsageModel.enter()
    Mockito.verify(mockUpdater, Mockito.times(1)).register(mockDetailedCpuUsage)
  }

  @Test
  fun testExit() {
    val mockDetailedCpuUsage = MockitoKt.mock<DetailedCpuUsage>();
    MockitoKt.whenever(mockCpuDataProvider.cpuUsage).thenReturn(mockDetailedCpuUsage)
    val mockLegendsCpu = Mockito.mock(CpuProfilerStage.CpuStageLegends::class.java, Mockito.RETURNS_DEEP_STUBS)
    val mockRangeSelectionModel = Mockito.mock(RangeSelectionModel::class.java, Mockito.RETURNS_DEEP_STUBS)
    MockitoKt.whenever(mockCpuDataProvider.legends).thenReturn(mockLegendsCpu)
    MockitoKt.whenever(mockCpuDataProvider.rangeSelectionModel).thenReturn(mockRangeSelectionModel)
    myLiveCpuUsageModel = LiveCpuUsageModel(myProfilers, mockCpuDataProvider, stage)
    val mockUpdater = Mockito.mock(Updater::class.java, Mockito.RETURNS_DEEP_STUBS)
    MockitoKt.whenever(myProfilers.updater).thenReturn(mockUpdater)
    myLiveCpuUsageModel.exit()
    Mockito.verify(mockUpdater, Mockito.times(1)).unregister(mockDetailedCpuUsage)
  }
}