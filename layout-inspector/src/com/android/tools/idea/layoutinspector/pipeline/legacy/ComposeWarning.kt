/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.pipeline.legacy

import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.ui.InspectorBannerService
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import org.jetbrains.android.dom.manifest.Manifest
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.idea.util.projectStructure.allModules

/**
 * Supply a warning banner if a LegacyClient is used for a compose application.
 */
class ComposeWarning(private val project: Project) {
  fun performCheck(client: InspectorClient) {
    if (isRunningCurrentProject(client) && isUsingCompose()) {
      val apiLevel = client.process.device.apiLevel
      val message = if (apiLevel < 29) {
        "To see compose nodes in the inspector please use a device with API >= 29"
      } else {
        "Cannot display compose nodes, try restarting the application"
      }
      val bannerService = InspectorBannerService.getInstance(project)
      bannerService?.addNotification(message, listOf(bannerService.DISMISS_ACTION))
    }
  }

  // Check if this is the current project, in which case we can check if this is a compose application with "isUsingCompose"
  private fun isRunningCurrentProject(client: InspectorClient): Boolean =
    project.allModules().any { module ->
      val facet = AndroidFacet.getInstance(module)
      val manifest = facet?.let { Manifest.getMainManifest(it) }
      val packageName = runReadAction { manifest?.`package`?.stringValue }
      packageName == client.process.name
    }

  private fun isUsingCompose(): Boolean =
    project.allModules().any { it.getModuleSystem().usesCompose }
}
