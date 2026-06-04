/*
 * Copyright (C) 2026 The Android Open Source Project
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
import com.android.tools.idea.execution.common.AppRunSettings
import com.android.tools.idea.execution.common.ApplicationDeployer
import com.android.tools.idea.projectsystem.ApplicationProjectContext
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.configuration.execution.AndroidWearWidgetConfigurationExecutor
import com.android.tools.idea.run.configuration.execution.WearWidgetLaunchOptions
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import icons.StudioIcons
import org.jetbrains.android.util.AndroidBundle

class AndroidWearWidgetConfigurationType :
  ConfigurationTypeBase(
    ID,
    AndroidBundle.message("android.wearwidget.configuration.type.name"),
    AndroidBundle.message("android.run.configuration.type.description"),
    StudioIcons.Wear.TILES_RUN_CONFIG,
  ),
  DumbAware {
  companion object {
    const val ID = "AndroidWearWidgetConfigurationType"
  }

  init {
    addFactory(
      object : AndroidWearConfigurationFactory(this) {
        override fun getId() = "AndroidWearWidgetConfigurationFactory"

        override fun createTemplateConfiguration(project: Project) = AndroidWearWidgetConfiguration(project, this)
      }
    )
  }
}

class AndroidWearWidgetConfiguration(project: Project, factory: ConfigurationFactory) : AndroidWearConfiguration(project, factory) {
  override val componentLaunchOptions: WearWidgetLaunchOptions = WearWidgetLaunchOptions()

  override fun getExecutor(
    environment: ExecutionEnvironment,
    deviceFutures: DeviceFutures,
    appRunSettings: AppRunSettings,
    apkProvider: ApkProvider,
    applicationContext: ApplicationProjectContext,
    deployer: ApplicationDeployer,
  ): AndroidConfigurationExecutor {
    return AndroidWearWidgetConfigurationExecutor(environment, deviceFutures, appRunSettings, apkProvider, applicationContext, deployer)
  }
}
