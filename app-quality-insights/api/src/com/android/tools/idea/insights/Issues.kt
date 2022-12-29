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
package com.google.services.firebase.insights.datamodel

import com.google.services.firebase.insights.proto.ErrorType
import com.google.services.firebase.insights.proto.IssueSignals
import com.google.services.firebase.insights.proto.ReportGroup
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent.CrashType
import icons.StudioIcons
import java.lang.IllegalStateException
import javax.swing.Icon

/**
 * Crash Fatality
 *
 * Selects fatal (crashes) or nonfatal (logged exceptions or errors) or ANR events.
 */
enum class Fatality {
  FATALITY_UNSPECIFIED,
  FATAL,
  NON_FATAL,
  ANR;

  fun toErrorTypeProto(): ErrorType =
    when (this) {
      FATALITY_UNSPECIFIED -> ErrorType.ERROR_TYPE_UNSPECIFIED
      FATAL -> ErrorType.FATAL
      NON_FATAL -> ErrorType.NON_FATAL
      ANR -> ErrorType.ANR
    }

  fun toCrashType(): CrashType =
    when (this) {
      FATALITY_UNSPECIFIED -> CrashType.UNKNOWN_TYPE
      FATAL -> CrashType.FATAL
      NON_FATAL -> CrashType.NON_FATAL
      ANR -> CrashType.UNKNOWN_TYPE
    }

  fun getIcon(withNote: Boolean = false): Icon? =
    when (this) {
      FATAL ->
        if (withNote) StudioIcons.AppQualityInsights.FATAL_WITH_NOTE
        else StudioIcons.AppQualityInsights.FATAL
      NON_FATAL ->
        if (withNote) StudioIcons.AppQualityInsights.NON_FATAL_WITH_NOTE
        else StudioIcons.AppQualityInsights.NON_FATAL
      ANR ->
        if (withNote) StudioIcons.AppQualityInsights.ANR_WITH_NOTE
        else StudioIcons.AppQualityInsights.ANR
      // This scenario shouldn't ever be reached.
      FATALITY_UNSPECIFIED -> null
    }
}

fun ErrorType.toFatality(): Fatality {
  return when (this) {
    ErrorType.ERROR_TYPE_UNSPECIFIED, ErrorType.UNRECOGNIZED -> Fatality.FATALITY_UNSPECIFIED
    ErrorType.FATAL -> Fatality.FATAL
    ErrorType.ANR -> Fatality.ANR
    ErrorType.NON_FATAL -> Fatality.NON_FATAL
  }
}

/**
 * Represents a problem detected in the app by Crashlytics.
 *
 * A single issue groups several different events, i.e. crashes or logged events.
 */
data class IssueDetails(
  // Issue id
  val id: String,
  // Title of the issue
  val title: String,
  // Subtitle of the issue
  val subtitle: String,
  // Fatal/non-fatal/ANR
  val fatality: Fatality,
  // The resource name for a sample event in this issue
  val sampleEvent: String,
  // Version that this issue was first seen
  val firstSeenVersion: String,
  // Version that this version was most recently seen
  val lastSeenVersion: String,
  // Number of unique devices.
  val impactedDevicesCount: Long,
  // number of unique events that occur for this issue
  val eventsCount: Long,
  // Issue signals.
  val signals: Set<SignalType>,
  // Provides a link to the containing issue on the Firebase console.
  // please note the link will be configured with the same time interval and filters as the request.
  val uri: String,
  val notesCount: Long
) {
  companion object {
    fun fromProto(proto: ReportGroup): IssueDetails {
      val issue = proto.issue
      val metrics = proto.metricsList.single()
      return IssueDetails(
        id = issue.id,
        title = issue.title,
        subtitle = issue.subtitle,
        fatality = issue.errorType.toFatality(),
        sampleEvent = issue.sampleEvent,
        firstSeenVersion = issue.firstSeenVersion,
        lastSeenVersion = issue.lastSeenVersion,
        impactedDevicesCount = metrics.impactedUsersCount.value,
        eventsCount = metrics.eventsCount.value,
        signals = issue.signalsList.mapNotNull { SignalType.fromProto(it) }.toSet(),
        uri = issue.uri,
        notesCount = issue.notesCount
      )
    }
  }
}

enum class IssueState {
  OPEN,
  OPENING,
  CLOSED,
  CLOSING;

  fun toProto(): com.google.services.firebase.insights.proto.Issue.State {
    return when (this) {
      OPEN -> com.google.services.firebase.insights.proto.Issue.State.OPEN
      CLOSED -> com.google.services.firebase.insights.proto.Issue.State.CLOSED
      else -> throw IllegalStateException("Only `OPEN` or `CLOSED` is allowed.")
    }
  }
}

@JvmInline value class IssueId(val id: String)

/** Represents an issue found by Crashlytics, including one representative event for it. */
data class Issue(
  val issueDetails: IssueDetails,
  val sampleEvent: Event,
  val state: IssueState = IssueState.OPEN,
  val hasPendingRequests: Boolean = false
) {
  val id: IssueId = IssueId(issueDetails.id)
  fun markPending() = copy(hasPendingRequests = true)
  fun markNotPending() = copy(hasPendingRequests = false)
  fun incrementNotesCount() =
    copy(issueDetails = issueDetails.copy(notesCount = issueDetails.notesCount + 1))
  fun decrementNotesCount() =
    copy(issueDetails = issueDetails.copy(notesCount = issueDetails.notesCount - 1))
}

/** Provides data about device and OS stats of an issue. */
data class DetailedIssueStats(val deviceStats: IssueStats<Double>, val osStats: IssueStats<Double>)

enum class SignalType(private val readableName: String, val icon: Icon?) {
  SIGNAL_UNSPECIFIED("All signal states", null),
  SIGNAL_EARLY("Early", StudioIcons.AppQualityInsights.EARLY_SIGNAL),
  SIGNAL_FRESH("Fresh", StudioIcons.AppQualityInsights.FRESH_SIGNAL),
  SIGNAL_REGRESSED("Regressed", StudioIcons.AppQualityInsights.REGRESSED_SIGNAL),
  SIGNAL_REPETITIVE("Repetitive", StudioIcons.AppQualityInsights.REPETITIVE_SIGNAL);

  override fun toString() = readableName

  fun toProto() =
    when (this) {
      SIGNAL_EARLY -> IssueSignals.Signal.SIGNAL_EARLY
      SIGNAL_FRESH -> IssueSignals.Signal.SIGNAL_FRESH
      SIGNAL_REGRESSED -> IssueSignals.Signal.SIGNAL_REGRESSED
      SIGNAL_REPETITIVE -> IssueSignals.Signal.SIGNAL_REPETITIVE
      SIGNAL_UNSPECIFIED -> null
    }

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

  companion object {
    fun fromProto(proto: IssueSignals) =
      when (proto.signal) {
        IssueSignals.Signal.SIGNAL_EARLY -> SIGNAL_EARLY
        IssueSignals.Signal.SIGNAL_FRESH -> SIGNAL_FRESH
        IssueSignals.Signal.SIGNAL_REGRESSED -> SIGNAL_REGRESSED
        IssueSignals.Signal.SIGNAL_REPETITIVE -> SIGNAL_REPETITIVE
        else -> null
      }
  }
}
