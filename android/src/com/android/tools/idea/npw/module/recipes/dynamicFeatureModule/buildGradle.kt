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

package com.android.tools.idea.npw.module.recipes.dynamicFeatureModule

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.npw.module.recipes.androidConfig
import com.android.tools.idea.npw.module.recipes.emptyPluginsBlock
import com.android.tools.idea.npw.module.recipes.getConfigurationName
import com.android.tools.idea.gradle.repositories.RepositoryUrlManager
import com.android.tools.idea.templates.resolveDependency
import com.android.tools.idea.wizard.template.GradlePluginVersion
import com.android.tools.idea.wizard.template.renderIf

fun buildGradle(
  baseFeatureName: String,
  agpVersion: GradlePluginVersion,
  isLibraryProject: Boolean,
  buildApiString: String,
  explicitBuildToolsVersion: Boolean,
  buildToolsVersion: String,
  minApi: String,
  targetApi: String,
  useAndroidX: Boolean
): String {

  val androidConfigBlock = androidConfig(
    buildApiString,
    explicitBuildToolsVersion,
    buildToolsVersion,
    minApi,
    targetApi,
    useAndroidX,
    "",
    isLibraryProject,
    hasTests = true
  )

  return """
${emptyPluginsBlock()}

$androidConfigBlock

dependencies {
    implementation project("${baseFeatureName}")
}
"""
}
