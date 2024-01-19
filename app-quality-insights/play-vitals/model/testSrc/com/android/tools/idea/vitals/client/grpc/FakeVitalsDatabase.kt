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
package com.android.tools.idea.vitals.client.grpc

import com.android.tools.idea.insights.AppInsightsIssue
import com.android.tools.idea.insights.EventData
import com.android.tools.idea.insights.FailureType
import com.android.tools.idea.insights.IssueAnnotation
import com.android.tools.idea.insights.IssueDetails
import com.android.tools.idea.insights.StacktraceGroup
import com.android.tools.idea.insights.client.toProtoTimestamp
import com.android.tools.idea.protobuf.Timestamp
import com.android.tools.idea.vitals.datamodel.VitalsConnection
import com.google.play.developer.reporting.AppVersion
import com.google.play.developer.reporting.DeviceId
import com.google.play.developer.reporting.DeviceModelSummary
import com.google.play.developer.reporting.ErrorIssue
import com.google.play.developer.reporting.ErrorReport
import com.google.play.developer.reporting.ErrorType
import com.google.play.developer.reporting.OsVersion
import java.util.concurrent.ConcurrentHashMap

private data class Cluster(val issue: ErrorIssue, val report: ErrorReport)

/**
 * A basic test database for Vitals issues meant to be used in conjunction with [FakeErrorsService].
 *
 * This database doesn't support multiple events and filtering of any kind.
 */
class FakeVitalsDatabase(private val connection: VitalsConnection) {

  private val database = ConcurrentHashMap<String, Cluster>()

  fun addIssue(issue: AppInsightsIssue) {
    addIssueWithCustomStackTrace(
      issue,
      issue.sampleEvent.eventData,
      issue.sampleEvent.stacktraceGroup.toRawString(),
    )
  }

  fun addIssueWithCustomStackTrace(
    issue: AppInsightsIssue,
    eventData: EventData,
    stacktrace: String,
  ) {
    val errorIssue = issue.issueDetails.toErrorIssue()
    val errorReport = eventToProto(issue, eventData, stacktrace)
    database[issue.id.value] = Cluster(errorIssue, errorReport)
  }

  fun clear() = database.clear()

  fun getIssues() = database.values.map { it.issue }

  fun getReportForIssue(errorIssueId: String) = database[errorIssueId]?.report

  private fun eventToProto(
    issue: AppInsightsIssue,
    eventData: EventData,
    stacktrace: String,
  ): ErrorReport =
    ErrorReport.newBuilder()
      .apply {
        name = "apps/${connection.appId}/errorReports/dummy_report_id"
        type = ErrorType.CRASH
        reportText = stacktrace
        this.issue = issue.id.value
        eventTime = Timestamp.newBuilder().apply { eventData.eventTime.toProtoTimestamp() }.build()
        deviceModel =
          DeviceModelSummary.newBuilder()
            .apply {
              deviceId =
                DeviceId.newBuilder()
                  .apply {
                    buildBrand = eventData.device.manufacturer
                    buildDevice = eventData.device.model
                    marketingName = eventData.device.displayName
                  }
                  .build()
            }
            .build()
        osVersion = toOsVersion(eventData.operatingSystemInfo.displayVersion.toLong())
      }
      .build()

  private fun IssueDetails.toErrorIssue(): ErrorIssue =
    ErrorIssue.newBuilder()
      .apply {
        name = "apps/${connection.appId}/errorIssues/${id.value}"
        type = fatality.toErrorType()
        cause = subtitle
        location = title
        errorReportCount = eventsCount
        distinctUsers = impactedDevicesCount
        issueUri = uri
        firstOsVersion = toOsVersion(lowestAffectedApiLevel)
        lastOsVersion = toOsVersion(highestAffectedApiLevel)
        firstAppVersion = toAppVersion(firstSeenVersion)
        lastAppVersion = toAppVersion(lastSeenVersion)
        addAllAnnotations(annotations.map { it.toProto() })
      }
      .build()
}

private fun FailureType.toErrorType() =
  when (this) {
    FailureType.UNSPECIFIED -> ErrorType.ERROR_TYPE_UNSPECIFIED
    FailureType.ANR -> ErrorType.APPLICATION_NOT_RESPONDING
    FailureType.FATAL,
    FailureType.NON_FATAL -> ErrorType.CRASH
  }

private fun toOsVersion(value: Long) = OsVersion.newBuilder().apply { apiLevel = value }.build()

private fun IssueAnnotation.toProto(): com.google.play.developer.reporting.IssueAnnotation =
  com.google.play.developer.reporting.IssueAnnotation.newBuilder()
    .apply {
      category = this@toProto.category
      title = this@toProto.title
      body = this@toProto.body
    }
    .build()

private fun toAppVersion(value: String) =
  AppVersion.newBuilder().apply { versionCode = value.toLong() }.build()

private fun StacktraceGroup.toRawString() =
  exceptions.joinToString("\n") { stack ->
    var stackString = stack.stacktrace.frames.joinToString("\n") { frame -> frame.rawSymbol }
    stackString =
      if (stack.rawExceptionMessage.isNotEmpty()) {
        "${stack.rawExceptionMessage}\n$stackString"
      } else stackString
    stackString
  }
