/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspector.api

abstract class AppInspectionException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Signals the connection being used to send commands has encountered some sort of error.
 *
 * Example: inspector connection is disposed.
 */
open class AppInspectionConnectionException(message: String): AppInspectionException(message)

/**
 * Signals that the inspector crashed on device, a case particularly interesting to users that also
 * will happen to interrupt the current exception.
 */
class AppInspectionCrashException(message: String): AppInspectionConnectionException(message)

/**
 * This happens when App Inspection service disposes an inspector because it was replaced
 * by a different project.
 */
class AppInspectorForcefullyDisposedException(message: String) : AppInspectionConnectionException(message)

/**
 * Base class for all service errors.
 */
abstract class AppInspectionServiceException(message: String, cause: Throwable? = null) : AppInspectionException(message, cause)

/**
 * Thrown when an error is encountered during inspector launch other than version incompatibility
 * (see [AppInspectionVersionIncompatibleException]).
 */
class AppInspectionLaunchException(message: String) : AppInspectionServiceException(message)

/**
 * Thrown when trying to launch an inspector on a device that cannot be found.
 */
class AppInspectionCannotFindAdbDeviceException(message: String) : AppInspectionServiceException(message)

/**
 * Thrown when trying to launch an inspector on a process that no longer exists.
 *
 * Note: This may not necessarily signal something is broken. We expect this to happen occasionally due to bad timing. For example: user
 * selects a process for inspection on device X right when X is shutting down.
 */
class AppInspectionProcessNoLongerExistsException(message: String, cause: Throwable? = null) : AppInspectionServiceException(message, cause)

/**
 * Thrown when launching an inspector that is incompatible with the version of the library in the running app.
 */
class AppInspectionVersionIncompatibleException(message: String) : AppInspectionServiceException(message)

/**
 * Thrown when the targeted library version does not exist in the app.
 */
class AppInspectionVersionMissingException(message: String) : AppInspectionServiceException(message)

/**
 * Thrown when the targeted library does not exist in the app.
 */
class AppInspectionLibraryMissingException(message: String) : AppInspectionServiceException(message)

/**
 * Thrown when target app was proguarded.
 */
class AppInspectionAppProguardedException(message: String) : AppInspectionServiceException(message)

/**
 * Thrown when an inspector artifact can't be resolved.
 */
class AppInspectionArtifactNotFoundException(message: String, cause: Throwable? = null) : AppInspectionServiceException(message, cause)