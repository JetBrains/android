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
import com.android.tools.idea.gservices.DevServicesDeprecationStatus.DEPRECATED
import com.android.tools.idea.gservices.DevServicesDeprecationStatus.UNSUPPORTED
import com.android.tools.idea.serverflags.ServerFlagService
import com.android.tools.idea.serverflags.protos.Date
import com.android.tools.idea.serverflags.protos.DevServicesDeprecationMetadata
import com.intellij.openapi.components.service
import java.text.DateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale

private const val DEV_SERVICES_DIR_NAME = "dev_services"
private const val SERVICE_NAME_PLACEHOLDER = "<service_name>"
private const val DEFAULT_SERVICE_NAME = "This service"
private const val DATE_PLACEHOLDER = "<date>"

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

  /** Developer services are deprecated and will be removed soon. Service remains available to user. */
  DEPRECATED,

  /** Developer Services are no longer supported and not available to user. */
  UNSUPPORTED;

  companion object {
    fun fromProto(status: DevServicesDeprecationMetadata.Status) = when(status) {
      // Unknown implies the flag is unavailable. To be on the safe side, we let the service stay SUPPORTED.
      DevServicesDeprecationMetadata.Status.STATUS_UNKNOWN,
      DevServicesDeprecationMetadata.Status.SUPPORTED -> SUPPORTED
      DevServicesDeprecationMetadata.Status.DEPRECATED -> DEPRECATED
      DevServicesDeprecationMetadata.Status.UNSUPPORTED -> UNSUPPORTED
    }
  }
}

interface DevServicesDeprecationDataProvider {
  /**
   * Returns the current deprecation policy data for a service of the given name.
   *
   * @param serviceName Name of the service
   * @param userFriendlyServiceName Name of the service that will be substituted and shown to the user
   */
  fun getCurrentDeprecationData(serviceName: String, userFriendlyServiceName: String = DEFAULT_SERVICE_NAME): DevServicesDeprecationData

  companion object {
    fun getInstance() = service<DevServicesDeprecationDataProvider>()
  }
}

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
  val date: LocalDate? = null
) {
  fun isDeprecated() = status == DEPRECATED

  fun isUnsupported() = status == UNSUPPORTED

  fun isSupported() = status == SUPPORTED
}

/** Provides [DevServicesDeprecationStatus] based on server flags. */
class ServerFlagBasedDevServicesDeprecationDataProvider : DevServicesDeprecationDataProvider {
  /**
   * Get the deprecation status of [serviceName] controlled by ServerFlags. Update the flags in
   * google3 to control the deprecation status.
   *
   * When [StudioFlags.USE_POLICY_WITH_DEPRECATE] flag is enabled, the status of service as well as
   * studio is checked. Service status is prioritized over studio. If service status is not available,
   * studio status is returned.
   *
   * @param serviceName The service name as used in the server flags storage in the backend. This
   *   should be the same as server_flags/server_configurations/dev_services/$serviceName.textproto.
   */
  override fun getCurrentDeprecationData(serviceName: String, userFriendlyServiceName: String): DevServicesDeprecationData {
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
    val serviceStatus = ServerFlagService.instance.getProto("$DEV_SERVICES_DIR_NAME/$serviceName", defaultInstance)
    val studioStatus = ServerFlagService.instance.getProto("$DEV_SERVICES_DIR_NAME/studio", defaultInstance)
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
      }
    )

  private fun DevServicesDeprecationMetadata.getDate() = if (this.hasDeprecationDate()) {
    deprecationDate.formatLocalized()
  } else {
    ""
  }

  private fun String.substituteValues(serviceName: String = DEFAULT_SERVICE_NAME, date: String) =
    replace(SERVICE_NAME_PLACEHOLDER, serviceName).replace(DATE_PLACEHOLDER, date)

  private fun Date.formatLocalized(): String {
    val calendar = Calendar.getInstance().apply {
      // Month is 0-indexed in Calendar
      set(year, month - 1, day)
    }
    return DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault()).format(calendar.time)
  }

  private fun Date.toLocalDate(): LocalDate = LocalDate.of(year, month - 1, day)
}
