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

import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent
import icons.StudioIcons
import javax.swing.Icon

@JvmInline value class IssueId(val value: String)

enum class IssueState {
  OPEN,
  OPENING,
  CLOSED,
  CLOSING,
}

/** Represents discovered issue, including one representative event for it. */
data class AppInsightsIssue(
  val issueDetails: IssueDetails,
  val sampleEvent: Event,
  val state: IssueState = IssueState.OPEN,
) {
  val id: IssueId = issueDetails.id

  fun incrementNotesCount() =
    copy(issueDetails = issueDetails.copy(notesCount = issueDetails.notesCount.inc()))

  fun decrementNotesCount() =
    copy(
      issueDetails = issueDetails.copy(notesCount = issueDetails.notesCount.dec().coerceAtLeast(0))
    )
}

enum class SignalType(private val readableName: String, val icon: Icon?, val description: String?) {
  SIGNAL_UNSPECIFIED("All signal states", null, null),
  SIGNAL_EARLY(
    "Early",
    StudioIcons.AppQualityInsights.EARLY_SIGNAL,
    "Issues with a high percentage of events within the first five seconds of a user's session",
  ),
  SIGNAL_FRESH(
    "Fresh",
    StudioIcons.AppQualityInsights.FRESH_SIGNAL,
    "New issues that appeared in the last seven days",
  ),
  SIGNAL_REGRESSED(
    "Regressed",
    StudioIcons.AppQualityInsights.REGRESSED_SIGNAL,
    "Issues that have reoccurred and that we've reopened",
  ),
  SIGNAL_REPETITIVE(
    "Repetitive",
    StudioIcons.AppQualityInsights.REPETITIVE_SIGNAL,
    "Issues with events that happened multiple times per user",
  );

  override fun toString() = readableName

  fun toLogProto() =
    when (this) {
      SIGNAL_EARLY ->
        AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.SignalFilter.EARLY_SIGNAL
      SIGNAL_FRESH ->
        AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.SignalFilter.FRESH_SIGNAL
      SIGNAL_REGRESSED ->
        AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.SignalFilter.REGRESSIVE_SIGNAL
      SIGNAL_REPETITIVE ->
        AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.SignalFilter.REPETITIVE_SIGNAL
      SIGNAL_UNSPECIFIED ->
        AppQualityInsightsUsageEvent.AppQualityInsightsFetchDetails.SignalFilter.UNKNOWN_SIGNAL
    }
}

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

data class IssueAnnotation(
  // e.g. "Potential fix", "Insight"
  val category: String,
  // e.g. "Slow Binder call"
  val title: String,
  val body: String,
) {
  companion object
}

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
