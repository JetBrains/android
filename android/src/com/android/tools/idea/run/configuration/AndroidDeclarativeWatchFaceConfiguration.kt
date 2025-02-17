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

import com.android.tools.idea.execution.common.AndroidConfigurationExecutor
import com.android.tools.idea.execution.common.stats.RunStats
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.ValidationError
import com.android.tools.idea.run.configuration.editors.AndroidDeclarativeWatchFaceConfigurationEditor
import com.android.tools.idea.run.configuration.execution.AndroidDeclarativeWatchFaceConfigurationExecutor
import com.android.tools.idea.run.configuration.execution.ApplicationDeployerImpl
import com.android.tools.idea.run.editor.DeployTargetProvider
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import icons.StudioIcons
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidBundle.message

/**
 * Represents a run configuration for Declarative Watch Faces. Declarative Watch Faces use the
 * [Watch Face Format](https://developer.android.com/training/wearables/wff), which is the
 * recommended approach to creating Watch Faces.
 */
class AndroidDeclarativeWatchFaceConfiguration(project: Project, factory: ConfigurationFactory) :
  AndroidRunConfigurationBase(project, factory, false) {

  override fun supportsRunningLibraryProjects(facet: AndroidFacet) =
    Pair(false, message("android.cannot.run.library.project.error"))

  override fun checkConfiguration(facet: AndroidFacet) = emptyList<ValidationError>()

  override fun getApplicableDeployTargetProviders(): List<DeployTargetProvider?> =
    deployTargetContext.getApplicableDeployTargetProviders(true)

  override fun getExecutor(
    environment: ExecutionEnvironment,
    facet: AndroidFacet,
    deviceFutures: DeviceFutures,
  ): AndroidConfigurationExecutor {
    val applicationIdProvider =
      project.getProjectSystem().getApplicationIdProvider(this)
        ?: throw RuntimeException("Cannot get ApplicationIdProvider")
    val apkProvider =
      environment.project.getProjectSystem().getApkProvider(this)
        ?: throw ExecutionException(message("android.run.configuration.not.supported", this::class.simpleName))
    val applicationDeployer =
      ApplicationDeployerImpl(environment.project, RunStats.from(environment))

    return AndroidDeclarativeWatchFaceConfigurationExecutor(
      environment,
      deviceFutures,
      applicationIdProvider,
      apkProvider,
      applicationDeployer,
    )
  }

  override fun getConfigurationEditor() = AndroidDeclarativeWatchFaceConfigurationEditor(project)
}

class AndroidDeclarativeWatchFaceConfigurationType :
  ConfigurationTypeBase(
    ID,
    message("android.declarative.watchface.configuration.type.name"),
    message("android.declarative.watchface.configuration.type.description"),
    StudioIcons.Wear.WATCH_FACE_RUN_CONFIG,
  ),
  DumbAware {
  companion object {
    const val ID = "AndroidDeclarativeWatchFaceConfigurationType"
  }

  init {
    if (!StudioFlags.WEAR_DECLARATIVE_WATCH_FACE_RUN_CONFIGURATION.get()) {
      throw ExtensionNotApplicableException.create()
    }
    addFactory(
      object : ConfigurationFactory(this) {
        override fun getId() = "AndroidDeclarativeWatchFaceConfigurationFactory"

        override fun createTemplateConfiguration(project: Project) =
          AndroidDeclarativeWatchFaceConfiguration(project, this)
      }
    )
  }
}
