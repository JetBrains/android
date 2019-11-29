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
package com.android.tools.idea.npw.module.recipes.wearModule

import com.android.tools.idea.npw.module.recipes.androidConfig
import com.android.tools.idea.npw.module.recipes.getConfigurationName
import com.android.tools.idea.npw.module.recipes.kotlinDependencies
import com.android.tools.idea.wizard.template.GradlePluginVersion
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.renderIf

fun wearBuildGradle(
  isLibraryProject: Boolean, // always false??
  packageName: String,
  buildApiString: String,
  explicitBuildToolsVersion: Boolean,
  buildToolsVersion: String,
  minApi: Int,
  targetApi: Int,
  useAndroidX: Boolean,
  language: Language,
  gradlePluginVersion: GradlePluginVersion
): String {
  val isApplicationProject = !isLibraryProject
  val pluginsBlock = "    " + when {
    isLibraryProject -> "apply plugin : \"com.android.library\""
    else -> "apply plugin : \"com.android.application\""
  }

  val androidConfigBlock = androidConfig(
    buildApiString,
    explicitBuildToolsVersion,
    buildToolsVersion,
    minApi,
    targetApi,
    useAndroidX,
    "",
    isLibraryProject,
    false,
    isApplicationProject,
    packageName,
    hasTests = true,
    canHaveCpp = false,
    canUseProguard = true
  )

  val kotlinDependenciesBlock = renderIf(language == Language.Kotlin) {
    kotlinDependencies(gradlePluginVersion)
  }

  val dependenciesBlock = """
  dependencies {
    ${getConfigurationName("compile", gradlePluginVersion)} fileTree (dir: "libs", include: ["*.jar"])
    $kotlinDependenciesBlock
  }
  """

  return """
    $pluginsBlock
    $androidConfigBlock
    $dependenciesBlock
  """
}

