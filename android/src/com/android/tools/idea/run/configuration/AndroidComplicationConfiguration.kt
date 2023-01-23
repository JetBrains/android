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
package com.android.tools.idea.run.configuration

import com.android.annotations.concurrency.WorkerThread
import com.android.tools.deployer.model.component.Complication
import com.android.tools.idea.execution.common.AppRunSettings
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.configuration.editors.AndroidComplicationConfigurationEditor
import com.android.tools.idea.run.configuration.execution.AndroidComplicationConfigurationExecutor
import com.android.tools.idea.run.configuration.execution.AndroidConfigurationExecutor
import com.android.tools.idea.run.configuration.execution.ComplicationLaunchOptions
import com.android.tools.idea.run.editor.DeployTarget
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.configurations.RuntimeConfigurationWarning
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import icons.StudioIcons
import org.jetbrains.android.util.AndroidBundle

class AndroidComplicationConfigurationType :
  ConfigurationTypeBase(
    ID,
    AndroidBundle.message("android.complication.configuration.type.name"),
    AndroidBundle.message("android.run.configuration.type.description"),
    StudioIcons.Wear.COMPLICATIONS_RUN_CONFIG
  ), DumbAware {
  companion object {
    const val ID = "AndroidComplicationConfigurationType"
  }

  init {
    addFactory(object : ConfigurationFactory(this) {
      override fun getId() = "AndroidComplicationConfigurationFactory"
      override fun createTemplateConfiguration(project: Project) = AndroidComplicationConfiguration(project, this)
    })
  }
}


class AndroidComplicationConfiguration(project: Project, factory: ConfigurationFactory) : AndroidWearConfiguration(project, factory) {
  data class ChosenSlot(var id: Int,
                        var type: Complication.ComplicationType?,
                        @Transient internal var slotFocused: Boolean = false,
                        @Transient internal var slotTypeFocused: Boolean = false) {
    // We need parameterless constructor for correct work of XmlSerializer. See [AndroidWearConfiguration.readExternal]
    @Suppress("unused")
    private constructor() : this(-1, Complication.ComplicationType.LONG_TEXT)
  }

  @WorkerThread
  override fun checkConfiguration() {
    super.checkConfiguration()
    // super.checkConfiguration() has already checked that module and componentName are not null.
    val rawTypes = getComplicationTypesFromManifest(module!!, componentLaunchOptions.componentName!!)
    if (componentLaunchOptions.chosenSlots.isEmpty()) {
      throw RuntimeConfigurationError(AndroidBundle.message("provider.slots.empty.error"))
    }
    componentLaunchOptions.verifyProviderTypes(parseRawComplicationTypes(rawTypes))
    checkRawComplicationTypes(rawTypes) // Make sure Errors are thrown before Warnings.
  }

  override val componentLaunchOptions: ComplicationLaunchOptions = ComplicationLaunchOptions()

  override fun getConfigurationEditor() = AndroidComplicationConfigurationEditor(project, this)

  override fun getExecutor(environment: ExecutionEnvironment,
                           deployTarget: DeployTarget,
                           appRunSettings: AppRunSettings,
                           applicationIdProvider: ApplicationIdProvider,
                           apkProvider: ApkProvider): AndroidConfigurationExecutor {
    return AndroidComplicationConfigurationExecutor(environment, deployTarget, appRunSettings, applicationIdProvider, apkProvider)
  }
}
