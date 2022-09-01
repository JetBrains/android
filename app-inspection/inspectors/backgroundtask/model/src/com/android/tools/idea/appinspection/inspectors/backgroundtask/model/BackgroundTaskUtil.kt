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
import com.android.tools.inspectors.common.api.stacktrace.StackFrameParser

fun WorkManagerInspectorProtocol.Event.getId(): String = when (oneOfCase) {
  WorkManagerInspectorProtocol.Event.OneOfCase.WORK_ADDED -> workAdded.work.id
  WorkManagerInspectorProtocol.Event.OneOfCase.WORK_UPDATED -> workUpdated.id
  WorkManagerInspectorProtocol.Event.OneOfCase.WORK_REMOVED -> workRemoved.id
  else -> throw RuntimeException()
}

fun BackgroundTaskInspectorProtocol.Event.getId(): Long = backgroundTaskEvent.taskId

private fun getTopExternalClassName(trace: String, filter: String): String? {
  return trace.lines()
    .mapNotNull { StackFrameParser.parseFrame(it) }
    .map { it.className }
    .firstOrNull { className -> filter != className }
}

/**
 * Returns the simple name of top external class from [trace].
 *
 * @param filter internal class name
 */
fun getTopExternalClassSimpleName(trace: String, filter: String): String? {
  return getTopExternalClassName(trace, filter)?.substringAfterLast('.')
}
