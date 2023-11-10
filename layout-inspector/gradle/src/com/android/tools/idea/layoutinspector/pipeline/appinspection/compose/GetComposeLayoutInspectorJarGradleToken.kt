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
package com.android.tools.idea.layoutinspector.pipeline.appinspection.compose

import com.android.tools.idea.appinspection.inspector.api.AppInspectorJar
import com.android.tools.idea.appinspection.inspector.api.launch.LibraryCompatibility
import com.android.tools.idea.appinspection.inspector.api.launch.LibraryCompatibilityInfo
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.projectsystem.GradleToken
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorCode

class GetComposeLayoutInspectorJarGradleToken :
  GetComposeLayoutInspectorJarToken<GradleProjectSystem>, GradleToken {
  override fun getRequiredCompatibility(): LibraryCompatibility = COMPOSE_INSPECTION_COMPATIBILITY

  override fun handleCompatibilityAndComputeVersion(
    notificationModel: NotificationModel,
    compatibility: LibraryCompatibilityInfo?,
    logErrorToMetrics: (AttachErrorCode) -> Unit,
    isRunningFromSourcesInTests: Boolean?
  ): String? =
    ComposeLayoutInspectorClient.handleCompatibilityAndComputeVersion(
      notificationModel,
      compatibility,
      logErrorToMetrics,
      isRunningFromSourcesInTests
    )

  override fun getAppInspectorJar(
    projectSystem: GradleProjectSystem,
    version: String?,
    notificationModel: NotificationModel,
    logErrorToMetrics: (AttachErrorCode) -> Unit,
    isRunningFromSourcesInTests: Boolean?
  ): AppInspectorJar? {
    val project = projectSystem.project
    return ComposeLayoutInspectorClient.getAppInspectorJar(
      project,
      version,
      notificationModel,
      logErrorToMetrics,
      isRunningFromSourcesInTests
    )
  }
}
