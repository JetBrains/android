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
package com.android.tools.idea.appinspection.inspectors.workmanager.model

import androidx.work.inspector.WorkManagerInspectorProtocol.Command
import androidx.work.inspector.WorkManagerInspectorProtocol.FetchAllWorkerInfoResponse
import androidx.work.inspector.WorkManagerInspectorProtocol.TrackWorkManagerCommand
import androidx.work.inspector.WorkManagerInspectorProtocol.WorkInfo
import com.android.tools.idea.appinspection.inspector.api.AppInspectorClient

/**
 * Class used to send commands to and handle events from the on-device work manager inspector through its [messenger].
 */
class WorkManagerInspectorClient(messenger: CommandMessenger) : AppInspectorClient(messenger) {
  private val _works = mutableListOf<WorkInfo>()
  val works: List<WorkInfo> get() = _works

  private val _worksChangedListeners = mutableListOf<() -> Unit>()
  fun addWorksChangedListener(listener: () -> Unit) = _worksChangedListeners.add(listener)

  override val rawEventListener = object : RawEventListener {
    override fun onRawEvent(eventData: ByteArray) {
      super.onRawEvent(eventData)
      handleFetchAllResponse(eventData)
    }
  }

  fun handleFetchAllResponse(responseBytes: ByteArray) {
    val response = FetchAllWorkerInfoResponse.parseFrom(responseBytes)
    _works.clear()
    _works.addAll(response.workersList)
    _worksChangedListeners.forEach { listener -> listener() }
  }
}