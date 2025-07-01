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

import com.android.tools.idea.gservices.DevServicesDeprecationStatus.DEPRECATED
import com.android.tools.idea.gservices.DevServicesDeprecationStatus.SUPPORTED
import com.android.tools.idea.gservices.DevServicesDeprecationStatus.UNSUPPORTED
import com.intellij.util.text.DateFormatUtil
import java.time.LocalDate
import java.time.ZoneId

data class DevServicesDeprecationData(
  // Header of the deprecation message
  val header: String,
  // Description of the deprecation message
  val description: String,
  // Link to provide more info
  val moreInfoUrl: String,
  // Show the update action button
  val showUpdateAction: Boolean,
  // Deprecation status of the service
  val status: DevServicesDeprecationStatus,
  // Date at which the service changes status
  val date: LocalDate? = null,
) {
  fun isDeprecated() = status == DEPRECATED

  fun isUnsupported() = status == UNSUPPORTED

  fun isSupported() = status == SUPPORTED

  fun formattedDate(): String =
    date?.let {
      DateFormatUtil.formatDate(
        java.util.Date.from(it.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant())
      )
    } ?: ""
}
