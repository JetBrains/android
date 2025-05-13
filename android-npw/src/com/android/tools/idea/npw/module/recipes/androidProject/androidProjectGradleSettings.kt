/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.npw.module.recipes.androidProject

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.extensions.isDaemonJvmCriteriaRequiredForNewProjects
import com.android.tools.idea.npw.builders.GradleSettingsBuilder
import com.android.tools.idea.wizard.template.renderIf
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmHelper
import java.net.URL

fun androidProjectGradleSettings(appTitle: String,
                                 gradleVersion: GradleVersion,
                                 agpVersion: AgpVersion,
                                 useGradleKts: Boolean,
                                 injectedRepositories: List<URL>): String {
  return renderIf(appTitle.isNotBlank()) {
    GradleSettingsBuilder(appTitle, useGradleKts) {
      withPluginManager(injectedRepositories)
      if (GradleDaemonJvmHelper.isDaemonJvmCriteriaRequiredForNewProjects(gradleVersion)) {
        withFoojayPlugin()
      }
      withDependencyResolutionManagement(injectedRepositories)
    }.build()
  }
}
