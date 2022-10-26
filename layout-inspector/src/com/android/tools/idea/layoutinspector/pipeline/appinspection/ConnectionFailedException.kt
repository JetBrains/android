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
package com.android.tools.idea.layoutinspector.pipeline.appinspection

import com.android.tools.idea.appinspection.inspector.api.AppInspectionAppProguardedException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionArtifactNotFoundException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionCannotFindAdbDeviceException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionLibraryMissingException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionProcessNoLongerExistsException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionServiceException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionVersionIncompatibleException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionVersionMissingException
import com.android.tools.idea.appinspection.inspector.api.launch.LibraryCompatbilityInfo
import com.android.tools.idea.layoutinspector.pipeline.ErrorInfo
import com.android.tools.idea.layoutinspector.pipeline.InspectorConnectionError
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.MINIMUM_COMPOSE_COORDINATE
import com.android.tools.idea.layoutinspector.pipeline.info
import com.android.tools.idea.transport.TransportNonExistingFileException
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorCode
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.io.HttpRequests
import java.net.UnknownHostException

const val GMAVEN_HOSTNAME = "maven.google.com"

/**
 * A problem was detected in one of the app inspection inspectors.
 *
 * Include a [message] for the user and an error [code] for analytics tracking.
 */
class ConnectionFailedException(message: String, val code: AttachErrorCode = AttachErrorCode.UNKNOWN_ERROR_CODE): Exception(message)

/**
 * Convert an exception to an [ErrorInfo] which has enough information for generating a message for the user and logging to analytics.
 * If the exception is unexpected (i.e. we cannot predict a proper user message) then the exception is logged to the crash db.
 */
val Throwable.errorCode: ErrorInfo
  get() = when (this) {
    is ConnectionFailedException -> code.info()
    is AppInspectionCannotFindAdbDeviceException -> AttachErrorCode.APP_INSPECTION_CANNOT_FIND_DEVICE.info()
    is AppInspectionProcessNoLongerExistsException -> AttachErrorCode.APP_INSPECTION_PROCESS_NO_LONGER_EXISTS.info()
    is AppInspectionVersionIncompatibleException -> AttachErrorCode.APP_INSPECTION_INCOMPATIBLE_VERSION.info()
    is AppInspectionVersionMissingException -> AttachErrorCode.APP_INSPECTION_VERSION_FILE_NOT_FOUND.info()
    is AppInspectionLibraryMissingException -> AttachErrorCode.APP_INSPECTION_MISSING_LIBRARY.info()
    is AppInspectionAppProguardedException -> AttachErrorCode.APP_INSPECTION_PROGUARDED_APP.info()
    is AppInspectionArtifactNotFoundException -> when {
      // The app may be using a SNAPSHOT for compose:ui:ui but have not specified the VM flag use.snapshot.jar:
      artifactCoordinate.version.endsWith("-SNAPSHOT") -> AttachErrorCode.APP_INSPECTION_SNAPSHOT_NOT_SPECIFIED
      message?.contains(GMAVEN_HOSTNAME) == true -> AttachErrorCode.APP_INSPECTION_FAILED_MAVEN_DOWNLOAD
      artifactCoordinate.sameArtifact(MINIMUM_COMPOSE_COORDINATE) -> AttachErrorCode.APP_INSPECTION_COMPOSE_INSPECTOR_NOT_FOUND
      else -> AttachErrorCode.APP_INSPECTION_ARTIFACT_NOT_FOUND
    }.info("artifact" to artifactCoordinate.toString())
    is AppInspectionServiceException -> {
      logUnexpectedError(InspectorConnectionError(this))
      AttachErrorCode.UNKNOWN_APP_INSPECTION_ERROR.info()
    }
    is TransportNonExistingFileException -> AttachErrorCode.TRANSPORT_PUSH_FAILED_FILE_NOT_FOUND.info("path" to path)
    else -> {
      logUnexpectedError(InspectorConnectionError(this))
      AttachErrorCode.UNKNOWN_ERROR_CODE.info()
    }
  }

/**
 * Convert a [LibraryCompatbilityInfo.Status] to [ErrorInfo].
 * <p/>An unexpected status will be logged to the crash db.
 */
val LibraryCompatbilityInfo.Status?.errorCode: ErrorInfo
  get() = when (this) {
    LibraryCompatbilityInfo.Status.INCOMPATIBLE -> AttachErrorCode.APP_INSPECTION_INCOMPATIBLE_VERSION
    LibraryCompatbilityInfo.Status.APP_PROGUARDED -> AttachErrorCode.APP_INSPECTION_PROGUARDED_APP
    LibraryCompatbilityInfo.Status.VERSION_MISSING -> AttachErrorCode.APP_INSPECTION_VERSION_FILE_NOT_FOUND
    LibraryCompatbilityInfo.Status.LIBRARY_MISSING -> AttachErrorCode.APP_INSPECTION_MISSING_LIBRARY
    else -> {
      logUnexpectedError(InspectorConnectionError("Unexpected status $this"))
      AttachErrorCode.UNKNOWN_APP_INSPECTION_ERROR
    }
  }.info()

/**
 * Log this unexpected exception to the crash db such that we can find these in go/studio-exceptions, but do not throw a new exception
 * since we cannot throw exception inside a coroutine error handler.
 */
fun logUnexpectedError(error: InspectorConnectionError) {
  try {
    Logger.getInstance(ConnectionFailedException::class.java).error(error)
  }
  catch (_: Throwable) {
  }
}
