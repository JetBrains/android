/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.vitals.datamodel

import com.android.tools.idea.insights.IssueDetails
import com.android.tools.idea.insights.client.toJavaInstant
import com.android.tools.idea.insights.model.event.Device
import com.android.tools.idea.insights.model.event.Event
import com.android.tools.idea.insights.model.event.EventData
import com.android.tools.idea.insights.model.event.OperatingSystemInfo
import com.android.tools.idea.insights.model.issue.IssueAnnotation
import com.android.tools.idea.insights.model.issue.IssueId
import com.android.tools.idea.insights.model.stacktrace.StackTraceGroupParser
import com.android.tools.idea.insights.model.vcs.AppVcsInfo
import com.google.play.developer.reporting.ErrorIssue
import com.google.play.developer.reporting.ErrorReport
import com.google.play.developer.reporting.ErrorType
import com.google.play.developer.reporting.IssueAnnotation as IssueAnnotationProto

internal fun ErrorIssue.toIssueDetails(): IssueDetails {
  return IssueDetails(
    id = IssueId(name.substringAfterLast("/")), // name format: apps/{app}/errorIssues/{issue}
    title = location, // e.g. java.lang.IllegalStateException
    subtitle = cause, // e,g, com.labpixies.flood.ExtraStepsActivity.onCreate
    fatality = type.toFailureType(),
    sampleEvent = sampleErrorReportsList.firstOrNull() ?: "", // Not in-use in vitals.
    firstSeenVersion = firstAppVersion.versionCode.toString(),
    lastSeenVersion = lastAppVersion.versionCode.toString(),
    lowestAffectedApiLevel = firstOsVersion.apiLevel,
    highestAffectedApiLevel = lastOsVersion.apiLevel,
    impactedDevicesCount = distinctUsers,
    eventsCount = errorReportCount,
    signals = emptySet(),
    uri = issueUri,
    notesCount = 0L,
    annotations = annotationsList.map { it.toIssueAnnotation() },
  )
}

internal fun ErrorReport.toSampleEvent(parser: StackTraceGroupParser): Event {
  return Event(
    name = name,
    eventData =
      EventData(
        device = Device.fromProto(deviceModel),
        operatingSystemInfo = OperatingSystemInfo.fromProto(osVersion),
        eventTime = eventTime.toJavaInstant(),
      ),
    stacktraceGroup =
      if (type == ErrorType.APPLICATION_NOT_RESPONDING) parser.parseThreadDump(reportText)
      else parser.parseException(reportText),
    appVcsInfo = AppVcsInfo.fromProto(vcsInformation),
  )
}

internal fun IssueAnnotationProto.toIssueAnnotation() = IssueAnnotation(category, title, body)
