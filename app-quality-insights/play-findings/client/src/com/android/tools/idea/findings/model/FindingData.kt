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
 * Base sealed class for structured data associated with a finding.
 *
 * Each subclass represents a specific payload structure for a particular finding type.
 */
sealed interface FindingData {
  /**
   * Payload for a DRM (Digital Rights Management) SDK compatibility issue.
   *
   * Indicates that the app is using a version of an SDK that has known issues on Google Play.
   *
   * @property drmPackageName The package name of the affected SDK.
   * @property drmDisplayName The user-facing display name of the SDK.
   * @property drmVersion The version of the SDK currently used in the app.
   * @property recommendedDrmVersion The SDK version recommended by the provider to upgrade to, if available.
   * @property learnMoreUrl An optional URL containing more details about the SDK issue.
   */
  data class DrmAppCompat(
    val drmPackageName: String,
    val drmDisplayName: String,
    val drmVersion: String,
    val recommendedDrmVersion: String?,
    val learnMoreUrl: String?,
  ) : FindingData

  /** Represents a finding type that has no extra structured data payload. */
  object Empty : FindingData

  /**
   * Fallback payload used when the finding type is unknown to this version of the client.
   *
   * Preserves the raw payload as a string (typically JSON) for debugging or forward-compatibility.
   *
   * @property rawData The raw string representation of the unparsed finding data.
   */
  data class Unknown(val rawData: String) : FindingData
}
