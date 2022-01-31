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
import com.android.tools.idea.run.configuration.execution.AndroidConfigurationExecutorBase
import com.android.tools.idea.run.editor.AndroidDebuggerContext
import com.intellij.execution.configurations.ModuleRunConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.module.Module

/**
 * Interfaces in this file eventually should become one single interface as we migrate all configuration on the new code flow and support all
 * features for them.
 */
interface ComponentSpecificConfiguration : ModuleRunConfiguration {
  val componentType: ComponentType
  val module: Module?
}

interface RunConfigurationWithDebugger : ModuleRunConfiguration {
  val androidDebuggerContext: AndroidDebuggerContext
}

interface RunConfigurationWithAndroidConfigurationExecutorBase {
  fun getExecutor(environment: ExecutionEnvironment): AndroidConfigurationExecutorBase
}