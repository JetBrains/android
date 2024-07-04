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

import com.android.tools.adtui.model.LineChartModel
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.RangedContinuousSeries
import com.android.tools.adtui.model.SeriesData
import com.android.tools.idea.flags.enums.PowerProfilerDisplayMode
import com.android.tools.profilers.cpu.LazyDataSeries
import java.util.function.Supplier

/**
 * Track model for Power counter in CPU capture stage.
 */
class PowerRailTrackModel(dataSeries: PowerCounterData,
                          viewRange: Range,
                          displayMode: PowerProfilerDisplayMode) : LineChartModel() {
  /**
   * [primaryCounterValues] represents the data to be displayed in the track, as well as in
   * the tooltip, while [secondaryCounterValues] serves as supplementary data, and will only be
   * displayed in the tooltip.
   */
  private val primaryCounterValues: List<SeriesData<Long>>
  private val secondaryCounterValues: List<SeriesData<Long>>

  init {
    // Note: The else conditions cover the PowerProfilerDisplayMode.HIDE case, but
    // the HIDE mode will not even be displayed, so this selection does not matter.
    val primaryValues = if (displayMode == PowerProfilerDisplayMode.CUMULATIVE) dataSeries.cumulativeData else dataSeries.deltaData
    val secondaryValues = if (displayMode == PowerProfilerDisplayMode.CUMULATIVE) dataSeries.deltaData else dataSeries.cumulativeData

    primaryCounterValues = primaryValues
    secondaryCounterValues = secondaryValues
  }

  val maxValue = primaryCounterValues.maxOfOrNull { it.value } ?: 0
  val minValue = primaryCounterValues.minOfOrNull { it.value } ?: 0

  // Instead of using the min value as the bottom of the range and thus not show the lowest value,
  // we can bring the range down by a value relative to the range of data. Experimentally, 1/4 of
  // the range works well to represent the data, but this is likely going to change.
  val baselineNormalizer = (maxValue - minValue) / 4

  val primaryPowerRailCounterSeries = RangedContinuousSeries("Power Rails", viewRange,
                                                             Range(minValue.toDouble() - baselineNormalizer, maxValue.toDouble()),
                                                             LazyDataSeries(Supplier { primaryCounterValues }))
  val secondaryPowerRailCounterSeries = RangedContinuousSeries("Power Rails", viewRange,
                                                               Range(minValue.toDouble() - baselineNormalizer, maxValue.toDouble()),
                                                               LazyDataSeries(Supplier { secondaryCounterValues }))

  init {
    // Only add the primary counter series to the line chart model as
    // it will be the only one displayed.
    add(primaryPowerRailCounterSeries)
  }

  companion object {
    // The power rails show consumed energy. Therefore, the
    // unit of a power rail is microwatt-seconds (µWs).
    const val POWER_RAIL_UNIT = "µWs"

    // This map defines the power rail groupings. Rails that map to the
    // same group will have their series data aggregated into one counter.
    val powerRailGroupMap = mapOf(
      "power.rails.modem" to "Cellular",
      "power.rails.radio.frontend" to "Cellular",
      "power.VSYS_PWR_MMWAVE_uws" to "Cellular",
      "power.rails.wifi.bt" to "WLAN",
      "power.rails.display" to "Display",
      "power.rails.cpu.big" to "CPU Big",
      "power.rails.cpu.mid" to "CPU Mid",
      "power.rails.cpu.little" to "CPU Little",
      "power.rails.gpu" to "GPU",
      "power.S8S_VDD_G3D_L2_uws" to "GPU",
      "power.rails.system.fabric" to "Infrastructure",
      "power.rails.memory.interface" to "Infrastructure",
      "power.rails.ddr.a" to "Memory",
      "power.rails.ddr.b" to "Memory",
      "power.rails.ddr.c" to "Memory",
      "power.L15M_VDD_SLC_M_uws" to "System Cache",
      "power.rails.aoc.memory" to "Sensor Core",
      "power.rails.aoc.logic" to "Sensor Core",
      "power.L7S_SENSORS_uws" to "Sensor Core",
      "power.rails.gps" to "GPS",
      "power.S1S_VDD_CAM_uws" to "Camera",
      "power.S6M_LLDO1_uws" to "Misc",
      "power.S8M_LLDO2_uws" to "Misc",
      "power.L2S_PLL_MIPI_UFS_uws" to "UFS (Disk)",
    )

    // List of filters used to detect power rails that should be hidden.
    // These filters take a higher priority than the 'powerRailGroupMap',
    // as rails can be mapped to a group, but also hidden.
    private val powerRailNameFilters = listOf<(String) -> Boolean>(
      { i -> i != "power.rails.memory.interface" },
      { i -> i != "power.rails.system.fabric" },
      { i -> i != "power.L15M_VDD_SLC_M_uws" },
      { i -> i != "power.S6M_LLDO1_uws" },
      { i -> i != "power.S8M_LLDO2_uws" },
    )

    // This method runs a power rail name through filters to see if it should be hidden.
    fun isPowerRailShown(railName: String): Boolean = powerRailNameFilters.fold(true) { filterResult, filter ->
      filterResult && filter.invoke(railName)
    }
  }
}