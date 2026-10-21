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

private val LOG = Logger.getInstance(FindingSeverity::class.java)

/** Represents the severity level of a Play Finding. */
enum class FindingSeverity {
  INFO,
  WARNING,
  SEVERE,
  BLOCKING;

  /** Converts this domain [FindingSeverity] to its Protobuf [ProtoFinding.Severity] counterpart. */
  fun toProto(): ProtoFinding.Severity =
    when (this) {
      INFO -> ProtoFinding.Severity.INFO
      WARNING -> ProtoFinding.Severity.WARNING
      SEVERE -> ProtoFinding.Severity.SEVERE
      BLOCKING -> ProtoFinding.Severity.BLOCKING
    }
}

/** Extension to convert a Protobuf [ProtoFinding.Severity] to its domain [FindingSeverity] counterpart. */
fun ProtoFinding.Severity.toDomain(): FindingSeverity =
  when (this) {
    ProtoFinding.Severity.INFO -> FindingSeverity.INFO
    ProtoFinding.Severity.WARNING -> FindingSeverity.WARNING
    ProtoFinding.Severity.SEVERE -> FindingSeverity.SEVERE
    ProtoFinding.Severity.BLOCKING -> FindingSeverity.BLOCKING
    else -> {
      LOG.warn("Encountered unknown severity: $this")
      // The Severity enum is officially frozen in the API.
      // Fall back to INFO as a safe default for robustness.
      FindingSeverity.INFO
    }
  }
