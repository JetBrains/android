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
package com.android.tools.idea.insights.model.issue

data class IssueDetails(
  // Issue id
  val id: IssueId,
  // Title of the issue
  val title: String,
  // Subtitle of the issue
  val subtitle: String,
  // Fatal/non-fatal/ANR
  val fatality: FailureType,
  // The resource name for a sample event in this issue
  val sampleEvent: String,
  // Version that this issue was first seen
  val firstSeenVersion: String,
  // Version that this version was most recently seen
  val lastSeenVersion: String,
  // The lowest API level in which this issue was seen
  val lowestAffectedApiLevel: Long,
  // The highest API level in which this issue was seen
  val highestAffectedApiLevel: Long,
  // Number of unique devices.
  val impactedDevicesCount: Long,
  // number of unique events that occur for this issue
  val eventsCount: Long,
  // Issue signals.
  val signals: Set<SignalType>,
  // Provides a link to the containing issue on the console.
  // please note the link will be configured with the same time interval and filters as the request.
  val uri: String,
  val notesCount: Long,
  // List of annotations for an issue. Annotations provide additional
  // information that may help in diagnosing and fixing the issue.
  val annotations: List<IssueAnnotation>,
)
