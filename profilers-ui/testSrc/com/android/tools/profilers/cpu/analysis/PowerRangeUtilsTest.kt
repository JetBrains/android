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
package com.android.tools.profilers.cpu.analysis

import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.SeriesData
import com.android.tools.profilers.cpu.analysis.PowerRailTableUtils.computeAveragePowerInRange
import com.android.tools.profilers.cpu.analysis.PowerRailTableUtils.computeCumulativeEnergyInRange
import com.android.tools.profilers.cpu.analysis.PowerRailTableUtils.computePowerUsageRange
import com.android.tools.profilers.cpu.analysis.PowerRailTableUtils.getLowerBoundDataInRange
import com.android.tools.profilers.cpu.analysis.PowerRailTableUtils.getUpperBoundDataInRange
import org.junit.Assert
import org.junit.Assert.fail
import org.junit.Test

class PowerRangeUtilsTest {
  @Test
  fun testComputedPowerUsageInRangeEntireRange() {
    // Test case: Range encapsulates all data, expect first element and last element used to compute energy and avg power
    val dataList = listOf(SeriesData(1000L, 10000L), SeriesData(12000L, 20000L), SeriesData(26000L, 35000L))

    val powerUsageRange = computePowerUsageRange(dataList, Range(0.0, 27000.0))
    val energyUsedInRange = computeCumulativeEnergyInRange(powerUsageRange)
    val avgPowerInRange = computeAveragePowerInRange(powerUsageRange)
    Assert.assertEquals(energyUsedInRange, 25000L)
    Assert.assertEquals(1000.0, avgPowerInRange, 0.000000000001)
  }

  @Test
  fun testComputedPowerUsageInRangeSubsetRange() {
    // Test case: Range encapsulates last two data points, expect second element and last element used to compute energy and avg power
    val dataList = listOf(SeriesData(10000L, 10000L), SeriesData(12000L, 20000L), SeriesData(26000L, 35000L))

    val powerUsageRange = computePowerUsageRange(dataList, Range(11000.0, 27000.0))
    val energyUsedInRange = computeCumulativeEnergyInRange(powerUsageRange)
    val avgPowerInRange = computeAveragePowerInRange(powerUsageRange)
    Assert.assertEquals(energyUsedInRange, 15000L)
    Assert.assertEquals(15000 / (14000.0 / 1000.0), avgPowerInRange, 0.000000000001)
  }

  @Test
  fun testComputedPowerUsageInRangeUpperBoundLessThanLower() {
    // Test case: Range's upper bound timestamp is less than lower bound's timestamp, expect total energy and avg power used to be 0
    val dataList = listOf(SeriesData(10000L, 10000L), SeriesData(12000L, 20000L), SeriesData(26000L, 35000L))

    val powerUsageRange = computePowerUsageRange(dataList, Range(12000.0, 11000.0))
    val energyUsedInRange = computeCumulativeEnergyInRange(powerUsageRange)
    val avgPowerInRange = computeAveragePowerInRange(powerUsageRange)
    Assert.assertEquals(energyUsedInRange, 0)
    Assert.assertEquals(avgPowerInRange, 0.0, 0.000000000001)
  }

  @Test
  fun testComputedPowerUsageInRangeUpperBoundEqualToLower() {
    // Test case: Range's upper bound timestamp is equal to lower bound's timestamp, expect total energy and avg power used to be 0
    val dataList = listOf(SeriesData(10000L, 10000L), SeriesData(12000L, 20000L), SeriesData(26000L, 35000L))

    val powerUsageRange = computePowerUsageRange(dataList, Range(11000.0, 11000.0))
    val energyUsedInRange = computeCumulativeEnergyInRange(powerUsageRange)
    val avgPowerInRange = computeAveragePowerInRange(powerUsageRange)
    Assert.assertEquals(energyUsedInRange, 0)
    Assert.assertEquals(avgPowerInRange, 0.0, 0.000000000001)
  }

  @Test
  fun testGetLowerBoundPowerValueEmptyList() {
    // Test case: Empty list, expect an exception to be thrown
    val emptyList = emptyList<SeriesData<Long>>()
    try {
      getLowerBoundDataInRange(emptyList, 5.0)
      // If the exception is not thrown, fail the test
      fail("Expected IndexOutOfBoundsException")
    }
    catch (e: AssertionError) {
      // Test passed
    }
  }

  @Test
  fun testGetLowerBoundPowerValueOneElementBelowMin() {
    // Test case: List with one element below the min, expect the value of the first element
    val dataList = listOf(SeriesData(1L, 10L))
    val result = getLowerBoundDataInRange(dataList, 5.0).value
    Assert.assertEquals(10L, result)
  }

  @Test
  fun testGetLowerBoundPowerValueOneElementAboveMin() {
    // Test case: List with one element above the min, expect the value of the first element
    val dataList = listOf(SeriesData(10L, 20L))
    val result = getLowerBoundDataInRange(dataList, 5.0).value
    Assert.assertEquals(20L, result)
  }

  @Test
  fun testGetLowerBoundPowerValueMultipleElements() {
    // Test case: List with multiple elements, expect the value of the element closest to the min
    val dataList = listOf(SeriesData(2L, 10L), SeriesData(4L, 20L), SeriesData(6L, 30L))
    val result = getLowerBoundDataInRange(dataList, 5.0).value
    Assert.assertEquals(30L, result)
  }

  @Test
  fun testGetLowerBoundPowerValueElementEqualToMin() {
    // Test case: List with multiple elements, expect the value of the element equal to value at min
    val dataList = listOf(SeriesData(2L, 10L), SeriesData(4L, 20L), SeriesData(6L, 30L))
    val result = getLowerBoundDataInRange(dataList, 4.0).value
    Assert.assertEquals(20L, result)
  }

  @Test
  fun testGetUpperBoundPowerValueEmptyList() {
    // Test case: Empty list, expect an exception to be thrown
    val emptyList = emptyList<SeriesData<Long>>()
    try {
      getUpperBoundDataInRange(emptyList, 10.0)
      // If the exception is not thrown, fail the test
      fail("Expected IndexOutOfBoundsException")
    }
    catch (e: AssertionError) {
      // Test passed
    }
  }

  @Test
  fun testGetUpperBoundPowerValueOneElementBelowMax() {
    // Test case: List with one element below the max, expect the value of the last element
    val dataList = listOf(SeriesData(5L, 10L))
    val result = getUpperBoundDataInRange(dataList, 10.0).value
    Assert.assertEquals(10L, result)
  }

  @Test
  fun testGetUpperBoundPowerValueOneElementAboveMax() {
    // Test case: List with one element above the max, expect the value of the last element
    val dataList = listOf(SeriesData(20L, 20L))
    val result = getUpperBoundDataInRange(dataList, 10.0).value
    Assert.assertEquals(20L, result)
  }

  @Test
  fun testGetUpperBoundPowerValueMultipleElements() {
    // Test case: List with multiple elements, expect the value of the element closest to the max
    val dataList = listOf(SeriesData(10L, 10L), SeriesData(12L, 20L), SeriesData(15L, 30L))
    val result = getUpperBoundDataInRange(dataList, 14.0).value
    Assert.assertEquals(20L, result)
  }

  @Test
  fun testGetUpperBoundPowerValueElementEqualToMax() {
    // Test case: List with multiple elements, expect the value of the element equal to value at max
    val dataList = listOf(SeriesData(2L, 10L), SeriesData(4L, 20L), SeriesData(6L, 30L))
    val result = getUpperBoundDataInRange(dataList, 4.0).value
    Assert.assertEquals(20L, result)
  }
}