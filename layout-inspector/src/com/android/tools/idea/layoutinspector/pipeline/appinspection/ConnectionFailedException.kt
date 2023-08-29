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
package com.android.tools.idea.layoutinspector.pipeline.appinspection

import com.android.tools.idea.appinspection.inspector.api.AppInspectionAppProguardedException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionArtifactNotFoundException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionCannotFindAdbDeviceException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionLibraryMissingException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionProcessNoLongerExistsException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionServiceException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionVersionIncompatibleException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionVersionMissingException
import com.android.tools.idea.appinspection.inspector.api.launch.LibraryCompatibilityInfo
import com.android.tools.idea.appinspection.inspector.api.launch.MinimumArtifactCoordinate
import com.android.tools.idea.layoutinspector.pipeline.InspectorConnectionError
import com.android.tools.idea.transport.TransportNonExistingFileException
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorCode
import com.intellij.openapi.diagnostic.Logger

const val GMAVEN_HOSTNAME = "maven.google.com"

/**
 * A problem was detected in one of the app inspection inspectors.
 *
 * @param message User visible error message.
 * @param code The error code used for analytics.
 */
class ConnectionFailedException(message: String, val code: AttachErrorCode) : Exception(message)

/**
 * An error description with an error [code] and optional [args] for generating a message.
 *
 * @param code The error code used for analytics.
 * @param args The arguments to use in creating a string representation of the error
 */
data class AttachErrorInfo(val code: AttachErrorCode, val args: Map<String, String>)

/**
 * Convert an exception to an [AttachErrorInfo] which has enough information for generating a
 * message for the user and logging to analytics.
 */
fun Throwable.toAttachErrorInfo(): AttachErrorInfo {
  return when (this) {
    is ConnectionFailedException -> code.toInfo()
    is AppInspectionCannotFindAdbDeviceException ->
      AttachErrorCode.APP_INSPECTION_CANNOT_FIND_DEVICE.toInfo()
    is AppInspectionProcessNoLongerExistsException ->
      AttachErrorCode.APP_INSPECTION_PROCESS_NO_LONGER_EXISTS.toInfo()
    is AppInspectionVersionIncompatibleException ->
      AttachErrorCode.APP_INSPECTION_INCOMPATIBLE_VERSION.toInfo()
    is AppInspectionVersionMissingException ->
      AttachErrorCode.APP_INSPECTION_VERSION_FILE_NOT_FOUND.toInfo()
    is AppInspectionLibraryMissingException ->
      AttachErrorCode.APP_INSPECTION_MISSING_LIBRARY.toInfo()
    is AppInspectionAppProguardedException -> AttachErrorCode.APP_INSPECTION_PROGUARDED_APP.toInfo()
    is TransportNonExistingFileException ->
      AttachErrorCode.TRANSPORT_PUSH_FAILED_FILE_NOT_FOUND.toInfo("path" to path)
    is AppInspectionArtifactNotFoundException -> this.toAttachErrorInfo()
    is AppInspectionServiceException -> AttachErrorCode.UNKNOWN_APP_INSPECTION_ERROR.toInfo()
    else -> AttachErrorCode.UNEXPECTED_ERROR.toInfo()
  }
}

/**
 * Convert a [LibraryCompatibilityInfo.Status] to [AttachErrorInfo].
 *
 * An unexpected status will be logged to the crash db.
 */
fun LibraryCompatibilityInfo.Status?.toAttachErrorInfo(): AttachErrorInfo {
  val errorCode =
    when (this) {
      LibraryCompatibilityInfo.Status.INCOMPATIBLE ->
        AttachErrorCode.APP_INSPECTION_INCOMPATIBLE_VERSION
      LibraryCompatibilityInfo.Status.APP_PROGUARDED ->
        AttachErrorCode.APP_INSPECTION_PROGUARDED_APP
      LibraryCompatibilityInfo.Status.VERSION_MISSING ->
        AttachErrorCode.APP_INSPECTION_VERSION_FILE_NOT_FOUND
      LibraryCompatibilityInfo.Status.LIBRARY_MISSING ->
        AttachErrorCode.APP_INSPECTION_MISSING_LIBRARY
      else -> {
        logUnexpectedError(InspectorConnectionError("Unexpected status $this"))
        AttachErrorCode.UNKNOWN_APP_INSPECTION_ERROR
      }
    }
  return errorCode.toInfo()
}

/**
 * Log unexpected exception, so we can see them in idea.log and are reported in the crash db at
 * go/studio-exceptions.
 */
fun logUnexpectedError(error: InspectorConnectionError) {
  try {
    Logger.getInstance(ConnectionFailedException::class.java).error(error)
  } catch (_: Throwable) {}
}

private fun AppInspectionArtifactNotFoundException.toAttachErrorInfo(): AttachErrorInfo {
  val errorCode =
    when {
      // The app may be using a SNAPSHOT for compose:ui:ui but have not specified the VM flag
      // use.snapshot.jar:
      artifactCoordinate.version.endsWith("-SNAPSHOT") ->
        AttachErrorCode.APP_INSPECTION_SNAPSHOT_NOT_SPECIFIED
      message?.contains(GMAVEN_HOSTNAME) == true ->
        AttachErrorCode.APP_INSPECTION_FAILED_MAVEN_DOWNLOAD
      MinimumArtifactCoordinate.COMPOSE_UI.sameArtifact(artifactCoordinate) ||
        MinimumArtifactCoordinate.COMPOSE_UI_ANDROID.sameArtifact(artifactCoordinate) ->
        AttachErrorCode.APP_INSPECTION_COMPOSE_INSPECTOR_NOT_FOUND
      else -> AttachErrorCode.APP_INSPECTION_ARTIFACT_NOT_FOUND
    }
  return errorCode.toInfo("artifact" to artifactCoordinate.toString())
}

fun AttachErrorCode.toInfo(vararg args: Pair<String, String>) = AttachErrorInfo(this, args.toMap())
