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
package com.android.tools.idea.testartifacts.instrumented

import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.util.GradleBuilds
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.execution.common.AndroidConfigurationExecutor
import com.android.tools.idea.testartifacts.instrumented.testsuite.export.ImportAndroidTestMatrixRunProfile
import com.intellij.execution.ExecutionException
import com.intellij.execution.runners.ExecutionEnvironment
import org.jetbrains.android.facet.AndroidFacet


class GradleAndroidTestRunConfigurationExecutorProvider : AndroidConfigurationExecutor.Provider {
  override fun createAndroidConfigurationExecutor(
    env: ExecutionEnvironment,
  ): AndroidConfigurationExecutor? {
    val configuration = if (env.runProfile is ImportAndroidTestMatrixRunProfile) {
      (env.runProfile as ImportAndroidTestMatrixRunProfile).initialConfiguration
    } else {
      env.runProfile
    }
    if (configuration !is AndroidTestRunConfiguration) return null

    val deviceFutures = env.getCopyableUserData(DeviceFutures.KEY)
    val module = configuration.configurationModule.module ?: throw ExecutionException("Module is not set")
    val facet = AndroidFacet.getInstance(module) ?: throw ExecutionException("Facet is not found")

    if (isRunAndroidTestUsingGradleSupported(facet)) {
      // Skip task for instrumentation tests run via UTP/AGP so that Gradle build
      // doesn't run twice per test run.
      env.putUserData(GradleBuilds.BUILD_SHOULD_EXECUTE, false)
      return GradleAndroidTestRunConfigurationExecutor(env, deviceFutures)
    }
    return null
  }

  private fun isRunAndroidTestUsingGradleSupported(facet: AndroidFacet): Boolean {
    val model = GradleAndroidModel.get(facet) ?: return false
    return model.androidProject.agpFlags.unifiedTestPlatformEnabled
  }
}