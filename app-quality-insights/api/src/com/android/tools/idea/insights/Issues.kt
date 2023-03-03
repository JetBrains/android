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

@JvmInline value class IssueId(val value: String)

interface Issue {
  val issueDetails: IssueDetails
  val sampleEvent: Event
}

interface IssueDetails {
  // Issue id
  val id: IssueId

  // Title of the issue
  val title: String

  // Subtitle of the issue
  val subtitle: String

  // Fatal/non-fatal/ANR
  val fatality: FailureType

  // The resource name for a sample event in this issue
  val sampleEvent: String

  // Version that this issue was first seen
  val firstSeenVersion: String

  // Version that this version was most recently seen
  val lastSeenVersion: String

  // Number of unique devices.
  val impactedDevicesCount: Long

  // number of unique events that occur for this issue
  val eventsCount: Long

  // Provides a link to the containing issue.
  val uri: String
}
