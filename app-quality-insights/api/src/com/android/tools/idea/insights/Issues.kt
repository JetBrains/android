/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.insights

data class IssueVariant(
  // Distinct identifier for the variant.
  val id: String,
  // The resource name for a sample event in this variant.
  val sampleEvent: String,
  // A link to the variants on the firebase console.
  val uri: String,
  // Number of unique devices.
  val impactedDevicesCount: Long,
  // number of unique events that occur for this issue
  val eventsCount: Long,
)

/** Source of request for fetching new issues. */
enum class FetchSource {
  // Offline mode
  BACKGROUND,
  // Refresh button clicked
  REFRESH,
  // Changes in filter selection
  FILTER,
  // Selected connection changed
  PROJECT_SELECTION,
}
