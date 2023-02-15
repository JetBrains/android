/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.network.view

import com.android.tools.adtui.chart.statechart.StateChart
import com.android.tools.adtui.chart.statechart.StateChartColorProvider
import com.android.tools.adtui.common.EnumColors
import com.android.tools.adtui.model.DefaultDataSeries
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.RangedSeries
import com.android.tools.adtui.model.StateChartModel
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.HttpData
import com.android.tools.idea.appinspection.inspectors.network.view.constants.NETWORK_RECEIVING_COLOR
import com.android.tools.idea.appinspection.inspectors.network.view.constants.NETWORK_RECEIVING_SELECTED_COLOR
import com.android.tools.idea.appinspection.inspectors.network.view.constants.NETWORK_SENDING_COLOR
import com.android.tools.idea.appinspection.inspectors.network.view.constants.NETWORK_WAITING_COLOR
import com.android.tools.idea.appinspection.inspectors.network.view.constants.TRANSPARENT_COLOR
import java.awt.Color

/**
 * Class responsible for rendering one or more sequential network requests, with each request
 * appearing as a horizontal bar where each stage of its lifetime (sending, receiving, etc.) is
 * highlighted with unique colors.
 */
class ConnectionsStateChart(dataList: List<HttpData>, range: Range) {
  constructor(data: HttpData, range: Range) : this(listOf(data), range)

  val colors =
    EnumColors.Builder<NetworkState>(2)
      .add(NetworkState.SENDING, NETWORK_SENDING_COLOR, NETWORK_SENDING_COLOR)
      .add(NetworkState.RECEIVING, NETWORK_RECEIVING_COLOR, NETWORK_RECEIVING_SELECTED_COLOR)
      .add(NetworkState.WAITING, NETWORK_WAITING_COLOR, NETWORK_WAITING_COLOR)
      .add(NetworkState.NONE, TRANSPARENT_COLOR, TRANSPARENT_COLOR)
      .build()

  val component = createChart(dataList, range)

  fun setHeightGap(gap: Float) {
    component.heightGap = gap
  }

  private fun createChart(dataList: Collection<HttpData>, range: Range): StateChart<NetworkState> {
    val series = DefaultDataSeries<NetworkState>()
    series.add(0, NetworkState.NONE)
    for (data in dataList) {
      if (data.connectionEndTimeUs == 0L) {
        continue
      }
      series.add(data.requestStartTimeUs, NetworkState.SENDING)
      if (data.responseStartTimeUs > 0) {
        series.add(data.responseStartTimeUs, NetworkState.RECEIVING)
      }
      series.add(data.connectionEndTimeUs, NetworkState.NONE)
    }
    val stateModel = StateChartModel<NetworkState>()
    val stateChart =
      StateChart(
        stateModel,
        colorProvider =
          object : StateChartColorProvider<NetworkState>() {
            override fun getColor(isMouseOver: Boolean, value: NetworkState): Color {
              return colors.getColor(value)
            }
          }
      )
    // TODO(b/122964201) Pass data range as 3rd param to RangedSeries to only show data from current
    // session
    stateModel.addSeries(RangedSeries(range, series))
    return stateChart
  }
}

enum class NetworkState {
  SENDING,
  RECEIVING,
  WAITING,
  NONE
}
