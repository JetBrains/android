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
package com.android.tools.idea.appinspection.inspectors.network.model

import com.android.tools.adtui.model.DataSeries
import com.android.tools.adtui.model.LineChartModel
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.RangedContinuousSeries
import com.android.tools.adtui.model.StreamingTimeline
import java.util.concurrent.Executor

class NetworkSpeedLineChartModel(
  timeline: StreamingTimeline,
  private val dataSource: NetworkInspectorDataSource,
  backgroundExecutor: Executor
) : LineChartModel(backgroundExecutor) {
  val trafficRange = Range(0.0, 4.0)

  val rxSeries =
    RangedContinuousSeries(
      NetworkTrafficLabel.BYTES_RECEIVED.getLabel(false),
      timeline.viewRange,
      trafficRange,
      createSeries(NetworkTrafficLabel.BYTES_RECEIVED),
      timeline.dataRange
    )

  val txSeries =
    RangedContinuousSeries(
      NetworkTrafficLabel.BYTES_SENT.getLabel(false),
      timeline.viewRange,
      trafficRange,
      createSeries(NetworkTrafficLabel.BYTES_SENT),
      timeline.dataRange
    )

  init {
    add(rxSeries)
    add(txSeries)
  }

  private fun createSeries(trafficType: NetworkTrafficLabel): DataSeries<Long> {
    return NetworkInspectorDataSeries(dataSource) { event ->
      if (trafficType == NetworkTrafficLabel.BYTES_RECEIVED) event.speedEvent.rxSpeed
      else event.speedEvent.txSpeed
    }
  }
}
