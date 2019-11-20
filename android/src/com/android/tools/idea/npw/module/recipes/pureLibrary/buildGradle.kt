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
package com.android.tools.idea.npw.module.recipes.pureLibrary

import com.android.tools.idea.npw.module.recipes.getConfigurationName
import com.android.tools.idea.wizard.template.GradlePluginVersion
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.renderIf


fun buildGradle(
  language: Language,
  javaVersion: String,
  gradlePluginVersion: GradlePluginVersion
): String {
  fun renderIfKotlin(content: String) = renderIf(language == Language.Kotlin) { content }

  val compileConfiguration = getConfigurationName("compile", gradlePluginVersion)
  return """
apply plugin: 'java-library'
${renderIfKotlin("apply plugin: 'kotlin'")}

dependencies {
  $compileConfiguration fileTree(dir: 'libs', include: ['*.jar'])
  ${renderIfKotlin("$compileConfiguration \"org.jetbrains.kotlin:kotlin-stdlib-jdk7:\$kotlin_version\"")}
}

sourceCompatibility = "${javaVersion}"
targetCompatibility = "${javaVersion}"
"""
}