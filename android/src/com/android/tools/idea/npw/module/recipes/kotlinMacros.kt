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
package com.android.tools.idea.npw.module.recipes

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ProjectTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor

const val stdlibBaseArtifact = "org.jetbrains.kotlin:kotlin-stdlib"

fun RecipeExecutor.addKotlinPlugins()  {
  applyPlugin("kotlin-android")
}

fun RecipeExecutor.addKotlinDependencies(androidX: Boolean) {
  if (!hasKotlinStdlib()) {
    addDependency("$stdlibBaseArtifact:\$${getKotlinVarName()}")
  }

  if (androidX) {
    addDependency("androidx.core:core-ktx:+")
  }
}

fun RecipeExecutor.setKotlinVersion(kotlinVersion: String) {
  setExtVar("${getKotlinVarName()}", kotlinVersion)
  addClasspathDependency("org.jetbrains.kotlin:kotlin-gradle-plugin:\$${getKotlinVarName()}", null)
}

fun RecipeExecutor.addKotlinToBaseProject(language: Language, kotlinVersion: String, isNewProject: Boolean = false) {
  if (!isNewProject && language == Language.Kotlin) {
    setKotlinVersion(kotlinVersion)
  }
}

fun RecipeExecutor.addKotlinIfNeeded(data: ProjectTemplateData, noKtx: Boolean = false) {
  if (data.language == Language.Kotlin) {
    addKotlinToBaseProject(data.language, data.kotlinVersion)
    addKotlinPlugins()
    addKotlinDependencies(data.androidXSupport && !noKtx)
  }
}

fun RecipeExecutor.addSupportWearableDependency() {
  addDependency("com.google.android.support:wearable:+")
  // This is needed for the com.google.android.support:wearable as a provided dependency otherwise it's warned by lint
  addDependency("com.google.android.wearable:wearable:+", "provided")
}

private fun RecipeExecutor.hasKotlinStdlib(): Boolean {
  val stdlibSuffixes = setOf("", "-jdk7", "-jdk8")
  return stdlibSuffixes.any { hasDependency(stdlibBaseArtifact + it) }
}

private fun RecipeExecutor.getKotlinVarName(): String =
  getClasspathDependencyVarName("org.jetbrains.kotlin:kotlin-gradle-plugin", "kotlin_version")
