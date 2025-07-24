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
import com.android.tools.idea.serverflags.DynamicServerFlagService
import com.android.tools.idea.serverflags.ServerFlagService
import com.android.tools.idea.serverflags.protos.Date
import com.android.tools.idea.serverflags.protos.DevServicesDeprecationMetadata
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.text.DateFormatUtil
import java.time.LocalDate
import java.util.Calendar
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val DEV_SERVICES_DIR_NAME = "dev_services"
private const val SERVICE_NAME_PLACEHOLDER = "<service_name>"
private const val DATE_PLACEHOLDER = "<date>"
private const val RESCHEDULE_DELAY_MILLIS = 7200000L // 2 hours in milliseconds

/** Provides [DevServicesDeprecationStatus] based on server flags. */
class ServerFlagBasedDevServicesDeprecationDataProvider(private val scope: CoroutineScope) :
  DevServicesDeprecationDataProvider, Disposable {
  private val listeners = CopyOnWriteArrayList<DeprecationChangeListener>()
  private var checkJob: Job? = null

  /**
   * Get the deprecation status of [serviceName] controlled by ServerFlags. Update the flags in
   * google3 to control the deprecation status.
   *
   * When [StudioFlags.USE_POLICY_WITH_DEPRECATE] flag is enabled, the status of service as well as
   * studio is checked. Service status is prioritized over studio. If service status is not
   * available, studio status is returned.
   *
   * **Use this method to get a one time value of the flag. This is preferred if your feature gets
   * called on user interactions (e.g. pop up menu item)**
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

  /**
   * Registers the provided [serviceName]. Returns a [StateFlow] containing the latest available
   * value for the service.
   *
   * **Use this method to register the listener and get notified of data changes. This is
   * preferred if your feature is in a toolwindow.**
   */
  override fun registerServiceForChange(
    serviceName: String,
    userFriendlyServiceName: String,
    disposable: Disposable,
  ): StateFlow<DevServicesDeprecationData> {
    val flow = MutableStateFlow(DevServicesDeprecationData.EMPTY)
    val listener = DeprecationChangeListener(serviceName, userFriendlyServiceName, flow)
    listeners.add(listener)
    Disposer.register(disposable) { unregisterListener(listener) }
    checkJob?.cancel()
    checkJob = scope.startCheckJobWithDelay(RESCHEDULE_DELAY_MILLIS)
    return flow.asStateFlow()
  }

  private fun unregisterListener(listener: DeprecationChangeListener) {
    listeners.remove(listener)
    if (listeners.isEmpty()) {
      checkJob?.cancel()
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
      DynamicServerFlagService.instance.getProto(
        "$DEV_SERVICES_DIR_NAME/$serviceName",
        defaultInstance,
      )
    val studioStatus =
      DynamicServerFlagService.instance.getProto("$DEV_SERVICES_DIR_NAME/studio", defaultInstance)
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

  private fun String.substituteValues(serviceName: String, date: String) =
    replace(SERVICE_NAME_PLACEHOLDER, serviceName).replace(DATE_PLACEHOLDER, date)

  private fun Date.formatLocalized(): String {
    val calendar =
      Calendar.getInstance().apply {
        // Month is 0-indexed in Calendar
        set(year, month - 1, day)
      }
    return DateFormatUtil.formatDate(calendar.time)
  }

  private fun checkNewDataAvailable() {
    DynamicServerFlagService.instance.updateFlags()
    listeners.forEach { listener ->
      listener.mutableStateFlow.update {
        checkServiceStatus(listener.serviceName).toDeprecationData(listener.userFriendlyServiceName)
      }
    }
  }

  private fun CoroutineScope.startCheckJobWithDelay(delayMillis: Long) = launch {
    while (true) {
      checkNewDataAvailable()
      delay(delayMillis)
    }
  }

  override fun dispose() {
    listeners.clear()
    checkJob?.cancel()
  }

  private data class DeprecationChangeListener(
    val serviceName: String,
    val userFriendlyServiceName: String,
    val mutableStateFlow: MutableStateFlow<DevServicesDeprecationData>,
  )
}
