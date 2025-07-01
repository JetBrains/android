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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gservices.DevServicesDeprecationStatus.SUPPORTED
import com.android.tools.idea.gservices.DevServicesDeprecationStatus.UNSUPPORTED
import com.android.tools.idea.serverflags.ServerFlagService
import com.android.tools.idea.serverflags.protos.Date
import com.android.tools.idea.serverflags.protos.DevServicesDeprecationMetadata
import com.intellij.util.text.DateFormatUtil
import java.time.LocalDate
import java.util.Calendar

private const val DEV_SERVICES_DIR_NAME = "dev_services"
private const val SERVICE_NAME_PLACEHOLDER = "<service_name>"
private const val DATE_PLACEHOLDER = "<date>"

/** Provides [DevServicesDeprecationStatus] based on server flags. */
class ServerFlagBasedDevServicesDeprecationDataProvider : DevServicesDeprecationDataProvider {
  /**
   * Get the deprecation status of [serviceName] controlled by ServerFlags. Update the flags in
   * google3 to control the deprecation status.
   *
   * When [StudioFlags.USE_POLICY_WITH_DEPRECATE] flag is enabled, the status of service as well as
   * studio is checked. Service status is prioritized over studio. If service status is not
   * available, studio status is returned.
   *
   * @param serviceName The service name as used in the server flags storage in the backend. This
   *   should be the same as server_flags/server_configurations/dev_services/$serviceName.textproto.
   */
  override fun getCurrentDeprecationData(
    serviceName: String,
    userFriendlyServiceName: String,
  ): DevServicesDeprecationData {
    return if (StudioFlags.USE_POLICY_WITH_DEPRECATE.get()) {
      val proto = checkServiceStatus(serviceName)
      return proto.toDeprecationData(userFriendlyServiceName)
    } else {
      getCurrentDeprecationData(serviceName)
    }
  }

  private fun getCurrentDeprecationData(serviceName: String): DevServicesDeprecationData {
    // Proto missing would imply the service is still supported.
    val proto =
      ServerFlagService.instance.getProtoOrNull(
        "dev_services/$serviceName",
        DevServicesDeprecationMetadata.getDefaultInstance(),
      ) ?: DevServicesDeprecationMetadata.getDefaultInstance()

    return proto.toDeprecationData()
  }

  private fun DevServicesDeprecationMetadata.isDeprecated() =
    hasHeader() || hasDescription() || hasMoreInfoUrl() || hasShowUpdateAction()

  private fun checkServiceStatus(serviceName: String): DevServicesDeprecationMetadata {
    val defaultInstance = DevServicesDeprecationMetadata.getDefaultInstance()
    val serviceStatus =
      ServerFlagService.instance.getProto("$DEV_SERVICES_DIR_NAME/$serviceName", defaultInstance)
    val studioStatus =
      ServerFlagService.instance.getProto("$DEV_SERVICES_DIR_NAME/studio", defaultInstance)
    return if (serviceStatus == defaultInstance) {
      studioStatus
    } else {
      serviceStatus
    }
  }

  private fun DevServicesDeprecationMetadata.toDeprecationData() =
    DevServicesDeprecationData(
      header,
      description,
      moreInfoUrl,
      showUpdateAction,
      if (isDeprecated()) UNSUPPORTED else SUPPORTED,
    )

  private fun DevServicesDeprecationMetadata.toDeprecationData(serviceName: String) =
    DevServicesDeprecationData(
      header.substituteValues(serviceName, getDate()),
      description.substituteValues(serviceName, getDate()),
      if (hasMoreInfoUrl()) moreInfoUrl else StudioFlags.DEFAULT_MORE_INFO_URL.get(),
      showUpdateAction,
      DevServicesDeprecationStatus.fromProto(status),
      if (hasDeprecationDate()) {
        LocalDate.of(deprecationDate.year, deprecationDate.month, deprecationDate.day)
      } else {
        null
      },
    )

  private fun DevServicesDeprecationMetadata.getDate() =
    if (this.hasDeprecationDate()) {
      deprecationDate.formatLocalized()
    } else {
      ""
    }

  private fun String.substituteValues(serviceName: String = DEFAULT_SERVICE_NAME, date: String) =
    replace(SERVICE_NAME_PLACEHOLDER, serviceName).replace(DATE_PLACEHOLDER, date)

  private fun Date.formatLocalized(): String {
    val calendar =
      Calendar.getInstance().apply {
        // Month is 0-indexed in Calendar
        set(year, month - 1, day)
      }
    return DateFormatUtil.formatDate(calendar.time)
  }
}