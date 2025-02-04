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
package com.android.tools.idea.run.configuration

import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.ValidationError
import com.android.tools.idea.run.configuration.editors.AndroidDeclarativeWatchFaceConfigurationEditor
import com.android.tools.idea.run.editor.DeployTargetProvider
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidBundle

/**
 * Represents a run configuration for Declarative Watch Faces. Declarative Watch Faces use the
 * [Watch Face Format](https://developer.android.com/training/wearables/wff), which is the
 * recommended approach to creating Watch Faces.
 */
class AndroidDeclarativeWatchFaceConfiguration(project: Project, factory: ConfigurationFactory) :
  AndroidRunConfigurationBase(project, factory, false) {

  override fun supportsRunningLibraryProjects(facet: AndroidFacet) =
    Pair(false, AndroidBundle.message("android.cannot.run.library.project.error"))

  override fun checkConfiguration(facet: AndroidFacet) = emptyList<ValidationError>()

  override fun getApplicableDeployTargetProviders(): List<DeployTargetProvider?> =
    deployTargetContext.getApplicableDeployTargetProviders(true)

  override fun getExecutor(
    env: ExecutionEnvironment,
    facet: AndroidFacet,
    deviceFutures: DeviceFutures,
  ) = null

  override fun getConfigurationEditor() = AndroidDeclarativeWatchFaceConfigurationEditor(project)
}
