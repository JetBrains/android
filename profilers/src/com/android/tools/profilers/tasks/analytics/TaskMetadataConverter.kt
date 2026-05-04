/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.profilers.tasks.analytics

import com.android.tools.profiler.proto.Common.ProfilerTaskType as ProtoTaskType
import com.android.tools.profiler.proto.ProfilerTaskMetadataProto.ProfilerTaskMetadata
import com.android.tools.profiler.proto.ProfilerTaskMetadataProto.ProfilerTaskMetadata.TaskAttachmentPoint as ProtoAttachmentPoint
import com.android.tools.profiler.proto.ProfilerTaskMetadataProto.ProfilerTaskMetadata.TaskDataOrigin as ProtoDataOrigin
import com.android.tools.profilers.tasks.ProfilerTaskType

/** Utility responsible for reconstructing [TaskMetadata] from serialized [ProfilerTaskMetadata] payloads. */
object TaskMetadataConverter {

  @JvmStatic
  fun fromProto(wrapper: ProfilerTaskMetadata): TaskMetadata {
    val metaData = wrapper.sessionMetadata
    val taskType = mapTaskType(metaData.taskType)

    val dataOrigin =
      when (wrapper.taskDataOrigin) {
        ProtoDataOrigin.NEW -> TaskDataOrigin.NEW
        ProtoDataOrigin.PAST_RECORDING -> TaskDataOrigin.PAST_RECORDING
        ProtoDataOrigin.IMPORTED -> TaskDataOrigin.IMPORTED
        else -> TaskDataOrigin.UNSPECIFIED
      }

    val attachmentPoint =
      when (wrapper.taskAttachmentPoint) {
        ProtoAttachmentPoint.NEW_PROCESS -> TaskAttachmentPoint.NEW_PROCESS
        ProtoAttachmentPoint.EXISTING_PROCESS -> TaskAttachmentPoint.EXISTING_PROCESS
        else -> TaskAttachmentPoint.UNSPECIFIED
      }

    return TaskMetadata(
      taskType = taskType,
      taskId = metaData.sessionId,
      taskDataOrigin = dataOrigin,
      taskAttachmentPoint = attachmentPoint,
      exposureLevel = metaData.exposureLevel,
      taskConfig = null,
    )
  }

  private fun mapTaskType(protoTaskType: ProtoTaskType): ProfilerTaskType {
    return if (protoTaskType == ProtoTaskType.UNSPECIFIED_TASK) {
      ProfilerTaskType.UNSPECIFIED
    } else {
      try {
        ProfilerTaskType.valueOf(protoTaskType.name)
      } catch (e: Exception) {
        ProfilerTaskType.UNSPECIFIED
      }
    }
  }
}
