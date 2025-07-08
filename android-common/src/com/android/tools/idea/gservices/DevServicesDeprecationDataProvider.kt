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

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import kotlinx.coroutines.flow.StateFlow

internal const val DEFAULT_SERVICE_NAME = "This service"

interface DevServicesDeprecationDataProvider {
  /**
   * Returns the current deprecation policy data for a service of the given name.
   *
   * @param serviceName Name of the service
   * @param userFriendlyServiceName Name of the service that will be substituted and shown to the
   *   user
   */
  fun getCurrentDeprecationData(
    serviceName: String,
    userFriendlyServiceName: String = DEFAULT_SERVICE_NAME,
  ): DevServicesDeprecationData

  /**
   * Register the [serviceName] and returns a [StateFlow] of [DevServicesDeprecationData] Stateflow
   * contains the latest available data.
   */
  fun registerServiceForChange(
    serviceName: String,
    userFriendlyServiceName: String = DEFAULT_SERVICE_NAME,
    disposable: Disposable,
  ): StateFlow<DevServicesDeprecationData>

  companion object {
    fun getInstance() = service<DevServicesDeprecationDataProvider>()
  }
}
