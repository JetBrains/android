/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.projectsystem

class DependencyManagementException(override val message: String, val errorCode: ErrorCodes): Exception() {
  /**
   * Error codes that describe the general category of failure.  The caller of dependency management
   * functions can use these error codes to determine a proper response to the exception.
   * (e.g. If the build system is not ready, then the caller can try again later.)
   */
  enum class ErrorCodes {
    /**
     * The project is missing key components that are required by the build system to perform the
     * requested action.
     */
    MALFORMED_PROJECT,
    /**
     * The requested operation is not supported.
     * (e.g. The underlying build system does not support the type of operation requested.)
     */
    UNSUPPORTED,
    /**
     * The build system is not ready to perform the requested operation.
     * (e.g. A sync in progress or a project that has not yet been fully initialized.)
     */
    BUILD_SYSTEM_NOT_READY,
    /**
     * The given source context is not a valid source context of the project.
     */
    BAD_SOURCE_CONTEXT
  }
}
