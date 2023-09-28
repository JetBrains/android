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
package com.android.tools.idea.run.configuration

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import org.jetbrains.android.util.AndroidUtils
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration

class BaselineProfileConfigurationProducer : LazyRunConfigurationProducer<GradleRunConfiguration>() {

  companion object {
    internal const val CONFIGURATION_NAME = "Generate Baseline Profile"
    internal val factory = GradleExternalTaskConfigurationType.getInstance().factory
    private const val ARG_ENABLED_RULES_BASELINE_PROFILE =
      "-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=baselineprofile"

    fun configure(configuration: GradleRunConfiguration, module: Module): Boolean {
      val targetProjectPath = module.targetProjectPath() ?: return false
      configuration.name = CONFIGURATION_NAME
      configuration.rawCommandLine = "$targetProjectPath:generateBaselineProfile $ARG_ENABLED_RULES_BASELINE_PROFILE"
      return true
    }
  }

  override fun getConfigurationFactory(): ConfigurationFactory = factory

  override fun isConfigurationFromContext(configuration: GradleRunConfiguration, context: ConfigurationContext): Boolean {
    val targetProjectPath = AndroidUtils.getAndroidModule(context)?.targetProjectPath() ?: return false
    return configuration.rawCommandLine.startsWith("$targetProjectPath:generateBaselineProfile") &&
           configuration.name == CONFIGURATION_NAME
  }

  override fun setupConfigurationFromContext(configuration: GradleRunConfiguration,
                                             context: ConfigurationContext,
                                             sourceElement: Ref<PsiElement>): Boolean {

    // If the studio flag is not enabled, skip entirely.
    if (!StudioFlags.GENERATE_BASELINE_PROFILE_GUTTER_ICON.get()) return false

    // Configure the new runConfiguration
    val module = AndroidUtils.getAndroidModule(context) ?: return false
    return configure(configuration, module)
  }

  // In case a configuration needs to be produced from context, we should defer preference to other configurations.
  override fun isPreferredConfiguration(self: ConfigurationFromContext?, other: ConfigurationFromContext?): Boolean {
    return false
  }
}

private fun Module.targetProjectPath() = ProjectBuildModel.getOrLog(project)
  ?.getModuleBuildModel(this)
  ?.android()
  ?.targetProjectPath()
