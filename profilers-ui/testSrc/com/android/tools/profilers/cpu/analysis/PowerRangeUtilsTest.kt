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
package com.android.tools.profilers.com.android.tools.profilers.cpu.analysis

import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.SeriesData
import com.android.tools.profilers.cpu.analysis.PowerRailTableUtils.computeCumulativeEnergyUsageInRange
import com.android.tools.profilers.cpu.analysis.PowerRailTableUtils.getLowerBoundDataInRange
import com.android.tools.profilers.cpu.analysis.PowerRailTableUtils.getUpperBoundDataInRange
import org.junit.Assert
import org.junit.Assert.fail
import org.junit.Test

class PowerRangeUtilsTest {
  @Test
  fun testComputeCumulativePowerUsageInRangeEntireRange() {
    // Test case: Range encapsulates all data, expect first elements value subtracted from last elements value
    val dataList = listOf(SeriesData(10L, 10L), SeriesData(12L, 20L), SeriesData(15L, 30L))

    val powerUsedInRange = computeCumulativeEnergyUsageInRange(dataList, Range(9.0, 16.0))
    Assert.assertEquals(powerUsedInRange, 20L)
  }

  @Test
  fun testComputeCumulativePowerUsageInRangeSubsetRange() {
    // Test case: Range encapsulates last two data points, expect second to last's elements value subtracted from last elements value
    val dataList = listOf(SeriesData(10L, 10L), SeriesData(12L, 20L), SeriesData(15L, 30L))

    val powerUsedInRange = computeCumulativeEnergyUsageInRange(dataList, Range(11.0, 16.0))
    Assert.assertEquals(powerUsedInRange, 10L)
  }

  @Test
  fun testComputeCumulativePowerUsageInRangeUpperBoundLessThanLower() {
    // Test case: Range's upper bound timestamp is less than lower bound's timestamp, expect power used to be 0
    val dataList = listOf(SeriesData(10L, 10L), SeriesData(12L, 20L), SeriesData(15L, 30L))

    val powerUsedInRange = computeCumulativeEnergyUsageInRange(dataList, Range(12.0, 11.0))
    Assert.assertEquals(powerUsedInRange, 0L)
  }

  @Test
  fun testComputeCumulativePowerUsageInRangeUpperBoundEqualToLower() {
    // Test case: Range's upper bound timestamp is equal to lower bound's timestamp, expect power used to be 0
    val dataList = listOf(SeriesData(10L, 10L), SeriesData(12L, 20L), SeriesData(15L, 30L))

    val powerUsedInRange = computeCumulativeEnergyUsageInRange(dataList, Range(11.0, 11.0))
    Assert.assertEquals(powerUsedInRange, 0L)
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