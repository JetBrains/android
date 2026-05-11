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
package com.android.tools.idea.publishing.play.client.type

import com.google.api.client.util.Key
import com.google.api.client.util.Value

data class ListTrackResponse(@field:Key var kind: String = "", @field:Key var tracks: List<Track> = emptyList())

data class Track(@field:Key var track: String = "", @field:Key var releases: List<Release> = emptyList())

data class Release(
  @field:Key var name: String = "",
  @field:Key var versionCodes: List<String> = emptyList(),
  @field:Key var releaseNotes: List<LocalizedText> = emptyList(),
  @field:Key var status: Status = Status.STATUS_UNSPECIFIED,
  @field:Key var userFraction: Double? = null,
  @field:Key var countryTargeting: CountryTargeting? = null,
  @field:Key var inAppUpdatePriority: Long = 0,
)

data class LocalizedText(@field:Key var language: String = "", @field:Key var text: String = "")

enum class Status {
  @Value("statusUnspecified") STATUS_UNSPECIFIED,
  @Value("draft") DRAFT,
  @Value("inProgress") IN_PROGRESS,
  @Value("halted") HALTED,
  @Value("completed") COMPLETED,
}

data class CountryTargeting(@field:Key var countries: List<String> = emptyList(), @field:Key var includeRestOfWorld: Boolean = false)
