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
package com.android.tools.idea.rendering

import com.android.tools.idea.projectsystem.AndroidProjectSettingsService
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId
import com.android.tools.idea.projectsystem.requiresAndroidModel
import com.android.tools.idea.util.dependsOn
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import org.jetbrains.android.sdk.AndroidSdkUtils

/** Studio specific implementation of [RenderDependencyInfo]. */
class StudioRenderDependencyInfo(private val module: Module) : RenderDependencyInfo {
  override val dependsOnAppCompat: Boolean
    get() = module.dependsOn(GoogleMavenArtifactId.APP_COMPAT_V7)

  override val dependsOnAndroidXAppCompat: Boolean
    get() = module.dependsOn(GoogleMavenArtifactId.ANDROIDX_APP_COMPAT_V7)

  override fun reportMissingSdk(logger: IRenderLogger) {
    val message = RenderProblem.create(ProblemSeverity.ERROR)
    logger.addMessage(message)
    message.htmlBuilder.addLink("No Android SDK found. Please ", "configure", " an Android SDK.",
                                logger.linkManager.createRunnableLink {
                                  val project = module.project
                                  val service = ProjectSettingsService.getInstance(project)
                                  if (project.requiresAndroidModel() && service is AndroidProjectSettingsService) {
                                    (service as AndroidProjectSettingsService).openSdkSettings()
                                    return@createRunnableLink
                                  }
                                  AndroidSdkUtils.openModuleDependenciesConfigurable(module)
                                })
  }
}