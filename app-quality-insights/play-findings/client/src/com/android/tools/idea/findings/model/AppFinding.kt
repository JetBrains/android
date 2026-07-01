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

/**
 * Represents a finding for an application from the Play Console.
 *
 * @property name The unique identifier of the finding.
 * @property type The type of the finding (e.g., [FindingType.DRM_APP_COMPAT]).
 * @property severity The severity (e.g., [FindingSeverity.WARNING]).
 * @property findingData The structured data payload. Use [FindingData.Empty] if no data is present.
 * @property affectedScopes Scopes within the app that this finding refers to.
 */
sealed interface AppFinding {
  val name: String
  val type: FindingType
  val severity: FindingSeverity
  val findingData: FindingData
  val affectedScopes: List<AffectedScope>

  /** Represents any recognized, supported finding from the Play Console. */
  data class Supported(
    override val name: String,
    override val type: FindingType,
    override val severity: FindingSeverity,
    override val findingData: FindingData,
    override val affectedScopes: List<AffectedScope> = emptyList(),
  ) : AppFinding

  /** Represents an unrecognized finding type for forward compatibility. */
  data class Unknown(
    override val name: String,
    override val severity: FindingSeverity,
    override val findingData: FindingData,
    override val affectedScopes: List<AffectedScope> = emptyList(),
  ) : AppFinding {
    override val type: FindingType = FindingType.UNKNOWN
  }
}
