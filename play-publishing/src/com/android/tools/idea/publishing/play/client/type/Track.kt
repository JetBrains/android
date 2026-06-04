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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class ListTrackResponse(val kind: String = "", val tracks: List<Track> = emptyList())

@Serializable data class Track(val track: String = "", val releases: List<Release> = emptyList())

@Serializable
data class Release(
  val name: String = "",
  val versionCodes: List<String> = emptyList(),
  val releaseNotes: List<LocalizedText> = emptyList(),
  val status: Status = Status.STATUS_UNSPECIFIED,
  val userFraction: Double? = null,
  val countryTargeting: CountryTargeting? = null,
  val inAppUpdatePriority: Long = 0,
)

@Serializable data class LocalizedText(val language: String = "", val text: String = "")

@Serializable
enum class Status {
  @SerialName("statusUnspecified") STATUS_UNSPECIFIED,
  @SerialName("draft") DRAFT,
  @SerialName("inProgress") IN_PROGRESS,
  @SerialName("halted") HALTED,
  @SerialName("completed") COMPLETED,
}

@Serializable data class CountryTargeting(val countries: List<String> = emptyList(), val includeRestOfWorld: Boolean = false)
