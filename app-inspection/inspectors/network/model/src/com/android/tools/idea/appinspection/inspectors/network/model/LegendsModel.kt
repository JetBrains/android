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

import com.android.tools.adtui.model.Interpolatable
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.formatter.NetworkTrafficFormatter
import com.android.tools.adtui.model.legend.LegendComponentModel
import com.android.tools.adtui.model.legend.SeriesLegend

private val TRAFFIC_AXIS_FORMATTER = NetworkTrafficFormatter(1, 5, 5)

class LegendsModel(
  speedLineChartModel: NetworkSpeedLineChartModel,
  range: Range,
  tooltip: Boolean
) : LegendComponentModel(range) {
  val rxLegend =
    SeriesLegend(
      speedLineChartModel.rxSeries,
      TRAFFIC_AXIS_FORMATTER,
      range,
      NetworkTrafficLabel.BYTES_RECEIVED.getLabel(tooltip),
      Interpolatable.SegmentInterpolator
    )
  val txLegend =
    SeriesLegend(
      speedLineChartModel.txSeries,
      TRAFFIC_AXIS_FORMATTER,
      range,
      NetworkTrafficLabel.BYTES_SENT.getLabel(tooltip),
      Interpolatable.SegmentInterpolator
    )

  init {
    add(rxLegend)
    add(txLegend)
  }
}
