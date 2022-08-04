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

import com.android.tools.deployer.model.component.ComponentType
import com.android.tools.idea.run.configuration.execution.DeployOptions
import com.android.tools.idea.run.editor.AndroidDebuggerContext
import com.intellij.execution.configurations.ModuleRunConfiguration
import com.intellij.openapi.module.Module

/**
 * Describes any start of Android Process that contains 2 steps: deploy and launch.
 */
interface AppRunSettings {
  val deployOptions: DeployOptions
  val componentLaunchOptions: ComponentLaunchOptions
  val module: Module?
}

interface ComponentLaunchOptions {
  val componentType: ComponentType
  val userVisibleComponentTypeName: String
}

interface WearSurfaceLaunchOptions : ComponentLaunchOptions {
  var componentName: String?
  val componentBaseClassesFqNames: Array<String>
}

interface RunConfigurationWithDebugger : ModuleRunConfiguration {
  val androidDebuggerContext: AndroidDebuggerContext
}

interface AppRunConfiguration : ModuleRunConfiguration {
  val appId: String?
}
