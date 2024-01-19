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

import com.android.tools.adtui.model.Range
import com.android.tools.idea.appinspection.inspectors.network.model.analytics.StubNetworkInspectorTracker
import com.android.tools.idea.appinspection.inspectors.network.model.connections.ConnectionData
import java.util.concurrent.TimeUnit
import studio.network.inspection.NetworkInspectorProtocol.Event

class FakeNetworkInspectorDataSource(
  httpEventList: List<Event> = emptyList(),
  private val speedEventList: List<Event> = emptyList(),
) : NetworkInspectorDataSource {
  var resetCalledCount = 0

  private val dataHandler =
    DataHandler(StubNetworkInspectorTracker()).apply {
      httpEventList.forEach { handleHttpConnectionEvent(it) }
    }

  private fun Event.isInRange(range: Range) =
    timestamp >= TimeUnit.MICROSECONDS.toNanos(range.min.toLong()) &&
      timestamp <= TimeUnit.MICROSECONDS.toNanos(range.max.toLong())

  override fun queryForConnectionData(range: Range): List<ConnectionData> =
    dataHandler.getHttpDataForRange(range) + dataHandler.getGrpcDataForRange(range)

  override fun queryForSpeedData(range: Range) = speedEventList.filter { it.isInRange(range) }

  override fun addOnExtendTimelineListener(listener: (Long) -> Unit) {}

  override fun start() {}

  override fun reset() {
    resetCalledCount++
  }
}
