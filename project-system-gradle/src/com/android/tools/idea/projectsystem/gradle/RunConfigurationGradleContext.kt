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

import com.android.tools.idea.gradle.project.build.invoker.TestCompileType
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.run.PreferGradleMake
import com.android.tools.idea.run.configuration.AndroidWearConfiguration
import com.android.tools.idea.util.androidFacet
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.execution.configurations.RunConfiguration
import org.jetbrains.android.facet.AndroidFacet
import java.util.Properties

data class RunConfigurationGradleContext(
  val androidFacet: AndroidFacet,
  val isTestConfiguration: Boolean,
  val testCompileType: TestCompileType,
  val isAdvancedProfilingEnabled: Boolean,
  val profilerProperties: Properties?,
  val alwaysDeployApkFromBundle: Boolean,
  val deployAsInstant: Boolean,
  val disabledDynamicFeatureModuleNames: Set<String>,
)

internal fun RunConfiguration.getGradleContext(): RunConfigurationGradleContext? {
  if (this !is AndroidRunConfigurationBase &&
    this !is AndroidWearConfiguration
  ) return null

  val preferGradleMake: PreferGradleMake = this as PreferGradleMake

  return RunConfigurationGradleContext(
    androidFacet = (this as? ModuleBasedConfiguration<*, *>)?.configurationModule?.module?.androidFacet ?: return null,
    isTestConfiguration = if (this is AndroidRunConfigurationBase) isTestConfiguration else false,
    testCompileType = preferGradleMake.testCompileMode,
    isAdvancedProfilingEnabled = (this as? AndroidRunConfigurationBase)?.profilerState?.ADVANCED_PROFILING_ENABLED == true,
    profilerProperties = (this as? AndroidRunConfigurationBase)?.profilerState?.toProperties(),
    alwaysDeployApkFromBundle = (this as? AndroidRunConfiguration)?.DEPLOY_APK_FROM_BUNDLE ?: false,
    deployAsInstant =  (this as? AndroidRunConfiguration)?.DEPLOY_AS_INSTANT ?: false,
    disabledDynamicFeatureModuleNames = (this as? AndroidRunConfiguration)?.disabledDynamicFeatures?.toSet().orEmpty()
  )
}

