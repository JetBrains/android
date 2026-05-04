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

import com.android.tools.profiler.proto.Common.Process.ExposureLevel
import com.android.tools.profiler.proto.Common.ProfilerTaskType as ProtoTaskType
import com.android.tools.profiler.proto.Common.SessionMetaData
import com.android.tools.profiler.proto.ProfilerTaskMetadataProto.ProfilerTaskMetadata
import com.android.tools.profiler.proto.ProfilerTaskMetadataProto.ProfilerTaskMetadata.TaskAttachmentPoint as ProtoAttachmentPoint
import com.android.tools.profiler.proto.ProfilerTaskMetadataProto.ProfilerTaskMetadata.TaskDataOrigin as ProtoDataOrigin
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Tests for [TaskMetadataConverter] to ensure accurate mapping from protobuf models to domain models. */
class TaskMetadataConverterTest {

  @Test
  fun testFromProtoUnspecifiedTask() {
    val sessionMetadata =
      SessionMetaData.newBuilder()
        .setSessionId(12345L)
        .setTaskType(ProtoTaskType.UNSPECIFIED_TASK)
        .setExposureLevel(ExposureLevel.UNKNOWN)
        .build()

    val proto =
      ProfilerTaskMetadata.newBuilder()
        .setSessionMetadata(sessionMetadata)
        // Implicitly leaving DataOrigin and AttachmentPoint as unspecified (default 0)
        .build()

    val metadata = TaskMetadataConverter.fromProto(proto)

    assertThat(metadata.taskId).isEqualTo(12345L)
    assertThat(metadata.taskType).isEqualTo(ProfilerTaskType.UNSPECIFIED)
    assertThat(metadata.taskDataOrigin).isEqualTo(TaskDataOrigin.UNSPECIFIED)
    assertThat(metadata.taskAttachmentPoint).isEqualTo(TaskAttachmentPoint.UNSPECIFIED)
    assertThat(metadata.exposureLevel).isEqualTo(ExposureLevel.UNKNOWN)
    assertThat(metadata.taskConfig).isNull()
  }

  @Test
  fun testFromProtoSpecifiedTask() {
    val sessionMetadata =
      SessionMetaData.newBuilder()
        .setSessionId(67890L)
        .setTaskType(ProtoTaskType.SYSTEM_TRACE)
        .setExposureLevel(ExposureLevel.DEBUGGABLE)
        .build()

    val proto =
      ProfilerTaskMetadata.newBuilder()
        .setSessionMetadata(sessionMetadata)
        .setTaskDataOrigin(ProtoDataOrigin.IMPORTED)
        .setTaskAttachmentPoint(ProtoAttachmentPoint.EXISTING_PROCESS)
        .build()

    val metadata = TaskMetadataConverter.fromProto(proto)

    assertThat(metadata.taskId).isEqualTo(67890L)
    assertThat(metadata.taskType).isEqualTo(ProfilerTaskType.SYSTEM_TRACE)
    assertThat(metadata.taskDataOrigin).isEqualTo(TaskDataOrigin.IMPORTED)
    assertThat(metadata.taskAttachmentPoint).isEqualTo(TaskAttachmentPoint.EXISTING_PROCESS)
    assertThat(metadata.exposureLevel).isEqualTo(ExposureLevel.DEBUGGABLE)
    assertThat(metadata.taskConfig).isNull()
  }
}
