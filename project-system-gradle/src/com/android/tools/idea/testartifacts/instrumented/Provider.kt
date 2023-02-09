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
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.LaunchOptions
import com.android.tools.idea.run.tasks.LaunchTasksProvider
import com.android.tools.idea.testartifacts.instrumented.configuration.AndroidTestConfiguration.Companion.getInstance
import com.intellij.execution.runners.ExecutionEnvironment
import org.jetbrains.android.facet.AndroidFacet

class CreateLaunchTasksProvider : LaunchTasksProvider.Provider {
  override fun
    createLaunchTasksProvider(
    runConfiguration: AndroidRunConfigurationBase,
    env: ExecutionEnvironment,
    facet: AndroidFacet,
    applicationIdProvider: ApplicationIdProvider,
    apkProvider: ApkProvider,
    launchOptions: LaunchOptions
  ): LaunchTasksProvider? {
    if (env.runProfile !is AndroidTestRunConfiguration) return null

    if (getInstance().RUN_ANDROID_TEST_USING_GRADLE && isRunAndroidTestUsingGradleSupported(facet)) {
      // Skip task for instrumentation tests run via UTP/AGP so that Gradle build
      // doesn't run twice per test run.
      env.putUserData(GradleBuilds.BUILD_SHOULD_EXECUTE, false)
      return GradleAndroidTestApplicationLaunchTasksProvider(env, facet, applicationIdProvider, launchOptions)
    }
    return null
  }

  private fun isRunAndroidTestUsingGradleSupported(facet: AndroidFacet): Boolean {
    val model = GradleAndroidModel.get(facet) ?: return false
    return model.androidProject.agpFlags.unifiedTestPlatformEnabled
  }
}