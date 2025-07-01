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
package com.android.tools.idea.gservices

import com.android.tools.idea.serverflags.protos.DevServicesDeprecationMetadata

/**
 * Status of services that rely on backend APIs for the current build of the IDE. Our SLA is to
 * support features that rely on backend APIs for a period of about a year past the release of a
 * specific IDE version. Beyond that timeline, users must upgrade to a newer IDE version to continue
 * using those features.
 *
 * See go/android-studio-developer-services-compat-policy and go/as-kill-feature-past-deadline.
 */
enum class DevServicesDeprecationStatus {
  /** Developer services are within the support window and available to user. */
  SUPPORTED,

  /**
   * Developer services are deprecated and will be removed soon. Service remains available to user.
   */
  DEPRECATED,

  /** Developer Services are no longer supported and not available to user. */
  UNSUPPORTED;

  companion object {
    fun fromProto(status: DevServicesDeprecationMetadata.Status) =
      when (status) {
        // Unknown implies the flag is unavailable. To be on the safe side, we let the service stay
        // SUPPORTED.
        DevServicesDeprecationMetadata.Status.STATUS_UNKNOWN,
        DevServicesDeprecationMetadata.Status.SUPPORTED -> SUPPORTED
        DevServicesDeprecationMetadata.Status.DEPRECATED -> DEPRECATED
        DevServicesDeprecationMetadata.Status.UNSUPPORTED -> UNSUPPORTED
      }
  }
}