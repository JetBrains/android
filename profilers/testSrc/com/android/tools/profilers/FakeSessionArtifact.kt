/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.profilers

import com.android.tools.idea.protobuf.GeneratedMessageV3
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.sessions.SessionArtifact

/**
 * A fake [SessionArtifact] for testing purposes.
 */
class FakeSessionArtifact(
  override val profilers: StudioProfilers,
  override val session: Common.Session,
  override val sessionMetaData: Common.SessionMetaData,
  override val artifactProto: GeneratedMessageV3,
  override val name: String = "FakeArtifact",
  override val isOngoing: Boolean = false,
  private val canExportArtifact: Boolean = true
) : SessionArtifact<GeneratedMessageV3> {

  override val timestampNs: Long = 0

  override fun doSelect() {
    // No-op
  }

  override val canExport: Boolean
    get() = canExportArtifact
}