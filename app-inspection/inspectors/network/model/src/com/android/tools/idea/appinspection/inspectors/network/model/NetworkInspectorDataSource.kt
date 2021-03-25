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

import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import studio.network.inspection.NetworkInspectorProtocol.Event
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The data backend of network inspector.
 *
 * It collects all of the events sent by the inspector and makes them available
 * for time queries.
 *
 * TODO(b/181877930): migrate to using a different data structure.
 */
class NetworkInspectorDataSource(
  messenger: AppInspectorMessenger,
  scope: CoroutineScope
) {

  private val _speedData = CopyOnWriteArrayList<Event>()
  val speedData: List<Event> = _speedData

  private val _httpData = CopyOnWriteArrayList<Event>()
  val httpData: List<Event> = _httpData

  init {
    scope.launch {
      messenger.eventFlow.collect {
        val event = Event.parseFrom(it)
        if (event.hasSpeedEvent()) {
          _speedData.add(event)
        }
        else if (event.hasHttpConnectionEvent()) {
          _httpData.add(event)
        }
      }
    }
  }
}