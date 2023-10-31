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

import com.android.ide.common.gradle.Version
import com.android.tools.idea.appinspection.api.AppInspectionApiServices
import com.android.tools.idea.appinspection.api.checkVersion
import com.android.tools.idea.appinspection.ide.InspectorArtifactService
import com.android.tools.idea.appinspection.ide.getOrResolveInspectorJar
import com.android.tools.idea.appinspection.inspector.api.AppInspectionArtifactNotFoundException
import com.android.tools.idea.appinspection.inspector.api.AppInspectorJar
import com.android.tools.idea.appinspection.inspector.api.launch.LibraryCompatbilityInfo
import com.android.tools.idea.appinspection.inspector.api.launch.LibraryCompatibility
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.android.tools.idea.layoutinspector.common.logDiagnostics
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.pipeline.appinspection.toAttachErrorInfo
import com.android.tools.idea.projectsystem.GradleToken
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorCode
import com.intellij.ui.EditorNotificationPanel
import kotlinx.coroutines.runBlocking

class GetComposeLayoutInspectorJarGradleToken :
  GetComposeLayoutInspectorJarToken<GradleProjectSystem>, GradleToken {
  override fun getRequiredCompatibility(): LibraryCompatibility = COMPOSE_INSPECTION_COMPATIBILITY
  override fun getAppInspectorJar(
    projectSystem: GradleProjectSystem,
    notificationModel: NotificationModel,
    apiServices: AppInspectionApiServices,
    process: ProcessDescriptor,
    logErrorToMetrics: (AttachErrorCode) -> Unit,
    isRunningFromSourcesInTests: Boolean?
  ): AppInspectorJar? {
    val project = projectSystem.project
    val compatibility = runBlocking {
      apiServices.checkVersion(
        project.name,
        process,
        MINIMUM_COMPOSE_COORDINATE.groupId,
        MINIMUM_COMPOSE_COORDINATE.artifactId,
        listOf(EXPECTED_CLASS_IN_COMPOSE_LIBRARY)
      )
    }
    val version =
      compatibility?.version?.takeIf {
        compatibility.status == LibraryCompatbilityInfo.Status.COMPATIBLE && it.isNotBlank()
      }
        ?: return ComposeLayoutInspectorClient.handleError(
          notificationModel,
          logErrorToMetrics,
          isRunningFromSourcesInTests,
          compatibility?.status.toAttachErrorInfo()
        )

    checkComposeVersion(notificationModel, version)

    return try {
      runBlocking {
        InspectorArtifactService.instance.getOrResolveInspectorJar(
          project,
          MINIMUM_COMPOSE_COORDINATE.copy(
            // TODO: workaround for kmp migration at 1.5.0-beta01 where the artifact id became
            // "ui-android"
            artifactId = determineArtifactId(version),
            version = version
          )
        )
      }
    } catch (exception: AppInspectionArtifactNotFoundException) {
      ComposeLayoutInspectorClient.handleError(
        notificationModel,
        logErrorToMetrics,
        isRunningFromSourcesInTests,
        exception.toAttachErrorInfo()
      )
    }
  }

  companion object {
    private const val EXPECTED_CLASS_IN_COMPOSE_LIBRARY = "androidx.compose.ui.Modifier"
    private val COMPOSE_INSPECTION_COMPATIBILITY =
      LibraryCompatibility(MINIMUM_COMPOSE_COORDINATE, listOf(EXPECTED_CLASS_IN_COMPOSE_LIBRARY))

    /**
     * Check for problems with the specified compose version, and display banners if appropriate.
     */
    private fun checkComposeVersion(notificationModel: NotificationModel, versionString: String) {
      val version = Version.parse(versionString)
      // b/237987764 App crash while fetching parameters with empty lambda was fixed in
      // 1.3.0-alpha03 and in 1.2.1
      // b/235526153 App crash while fetching component tree with certain Borders was fixed in
      // 1.3.0-alpha03 and in 1.2.1
      if (
        version >= Version.parse("1.3.0-alpha03") ||
          version.minor == 2 && version >= Version.parse("1.2.1")
      )
        return
      val versionUpgrade = if (version.minor == 3) "1.3.0" else "1.2.1"
      val message =
        LayoutInspectorBundle.message(
          COMPOSE_MAY_CAUSE_APP_CRASH_KEY,
          versionString,
          versionUpgrade
        )
      logDiagnostics(
        ComposeLayoutInspectorClient::class.java,
        "Compose version warning, message: %s",
        message
      )
      notificationModel.addNotification(
        COMPOSE_MAY_CAUSE_APP_CRASH_KEY,
        message,
        EditorNotificationPanel.Status.Warning
      )
      // Allow the user to connect and inspect compose elements because:
      // - b/235526153 is uncommon
      // - b/237987764 only happens if the kotlin compiler version is at least 1.6.20 (which we
      // cannot reliably detect)
    }

    private val KMP_MIGRATION_VERSION = Version.parse("1.5.0-beta01")

    @VisibleForTesting
    fun determineArtifactId(versionIdentifier: String) =
      Version.parse(versionIdentifier).let {
        if (it < KMP_MIGRATION_VERSION) "ui" else "ui-android"
      }
  }
}
