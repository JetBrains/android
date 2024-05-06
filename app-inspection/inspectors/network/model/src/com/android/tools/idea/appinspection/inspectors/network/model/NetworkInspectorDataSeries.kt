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
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.SeriesData
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import studio.network.inspection.NetworkInspectorProtocol.Event

/**
 * The amount of "padding" used when querying for data.
 *
 * Note, the padding does not throw off the accuracy of the model at all. It is used to guarantee
 * enough sampling points so a result can be interpolated.
 */
private const val TIME_BUFFER_US = 1000000

class NetworkInspectorDataSeries<T>(
  private val dataSource: NetworkInspectorDataSource,
  private val transform: (Event) -> T
) : DataSeries<T> {

  override fun getDataForRange(range: Range): List<SeriesData<T>> {
    return runBlocking {
      dataSource
        .queryForSpeedData(Range(range.min - TIME_BUFFER_US, range.max + TIME_BUFFER_US))
        .map { event ->
          SeriesData(TimeUnit.NANOSECONDS.toMicros(event.timestamp), transform(event))
        }
    }
  }
}
