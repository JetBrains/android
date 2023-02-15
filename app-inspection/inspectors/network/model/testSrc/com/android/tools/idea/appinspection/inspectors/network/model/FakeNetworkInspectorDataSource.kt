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
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import studio.network.inspection.NetworkInspectorProtocol
import studio.network.inspection.NetworkInspectorProtocol.Event

class FakeNetworkInspectorDataSource(
  private val httpEventList: List<Event> = emptyList(),
  private val speedEventList: List<Event> = emptyList()
) : NetworkInspectorDataSource {
  private fun Event.isInRange(range: Range) =
    timestamp >= TimeUnit.MICROSECONDS.toNanos(range.min.toLong()) &&
      timestamp <= TimeUnit.MICROSECONDS.toNanos(range.max.toLong())

  override val connectionEventFlow: Flow<NetworkInspectorProtocol.HttpConnectionEvent> = flow {}

  override suspend fun queryForHttpData(range: Range) = httpEventList.filter { it.isInRange(range) }

  override suspend fun queryForSpeedData(range: Range) =
    speedEventList.filter { it.isInRange(range) }
}
