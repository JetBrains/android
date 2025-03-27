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
package com.android.tools.idea.insights

import com.android.tools.idea.gservices.DevServicesDeprecationData
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.StateFlow

/** Project-level [Service] that provides App Insights data for Android app modules. */
interface AppInsightsConfigurationManager {
  val project: Project

  val configuration: StateFlow<AppInsightsModel>

  val offlineStatusManager: OfflineStatusManager

  val deprecationData: DevServicesDeprecationData

  fun refreshConfiguration() = Unit
}
