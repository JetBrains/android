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
package com.android.tools.idea.appinspection.inspectors.backgroundtask.model

import androidx.work.inspection.WorkManagerInspectorProtocol
import backgroundtask.inspection.BackgroundTaskInspectorProtocol


/**
 * A Wrapper class that contains either [WorkManagerInspectorProtocol.Event] or [BackgroundTaskInspectorProtocol.Event].
 */
class EventWrapper(val case: Case, data: ByteArray) {
  enum class Case {
    WORK,
    BACKGROUND_TASK
  }

  val workEvent =
    if (case == Case.WORK) WorkManagerInspectorProtocol.Event.parseFrom(data)
    else WorkManagerInspectorProtocol.Event.getDefaultInstance()
  val backgroundTaskEvent =
    if (case == Case.BACKGROUND_TASK) BackgroundTaskInspectorProtocol.Event.parseFrom(data)
    else BackgroundTaskInspectorProtocol.Event.getDefaultInstance()
}
