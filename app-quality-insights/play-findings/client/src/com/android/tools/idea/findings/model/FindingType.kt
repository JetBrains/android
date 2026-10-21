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
package com.android.tools.idea.findings.model

import com.google.play.androidpublisher.v3.Finding as ProtoFinding
import com.intellij.openapi.diagnostic.Logger

private val LOG = Logger.getInstance(FindingType::class.java)

/** Represents the type of Play Finding. */
enum class FindingType {
  /** DRM App Compatibility finding. */
  DRM_APP_COMPAT,

  /** Fallback for unrecognized finding types to ensure forward compatibility. */
  UNKNOWN;

  /** Converts this domain [FindingType] to its Protobuf [ProtoFinding.Type] counterpart, or null if [UNKNOWN]. */
  fun toProto(): ProtoFinding.Type? =
    when (this) {
      DRM_APP_COMPAT -> ProtoFinding.Type.DRM_APP_COMPAT
      UNKNOWN -> null
    }
}

/** Extension to convert a Protobuf [ProtoFinding.Type] to its domain [FindingType] counterpart. */
fun ProtoFinding.Type.toDomain(): FindingType =
  when (this) {
    ProtoFinding.Type.DRM_APP_COMPAT -> FindingType.DRM_APP_COMPAT
    else -> {
      LOG.warn("Encountered unknown finding type: $this")
      FindingType.UNKNOWN
    }
  }
