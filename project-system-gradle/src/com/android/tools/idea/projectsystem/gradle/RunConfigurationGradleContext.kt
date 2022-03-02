/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.projectsystem.gradle

import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.run.configuration.AndroidWearConfiguration
import com.android.tools.idea.run.editor.ProfilerState
import com.android.tools.idea.util.androidFacet
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.execution.configurations.RunConfiguration
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal class RunConfigurationGradleContext(
  val androidFacet: AndroidFacet,
  val isTestConfiguration: Boolean,
  val profilingMode: ProfilerState.ProfilingMode,
  val isAdvancedProfilingEnabled: Boolean,
  val alwaysDeployApkFromBundle: Boolean
)

internal fun RunConfiguration.getGradleContext(): RunConfigurationGradleContext? {
  if (this !is AndroidRunConfigurationBase &&
    this !is AndroidWearConfiguration
  ) return null

  return RunConfigurationGradleContext(
    androidFacet = safeAs<ModuleBasedConfiguration<*, *>>()?.configurationModule?.module?.androidFacet ?: return null,
    isTestConfiguration = if (this is AndroidRunConfigurationBase) isTestConfiguration else false,
    profilingMode = (this as? AndroidRunConfigurationBase)?.profilerState?.PROFILING_MODE ?: ProfilerState.ProfilingMode.NOT_SET,
    isAdvancedProfilingEnabled = (this as? AndroidRunConfigurationBase)?.profilerState?.ADVANCED_PROFILING_ENABLED == true,
    alwaysDeployApkFromBundle = (this as? AndroidRunConfiguration)?.let(AndroidRunConfiguration::shouldDeployApkFromBundle) ?: false
  )
}

