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


fun RecipeExecutor.addKotlinDependencies(androidX: Boolean, targetApi: Int) {
  if (androidX) {
    val dependency = if (targetApi < 31) "androidx.core:core-ktx:1.6.+" else "androidx.core:core-ktx:1.9.+"
    val minRev = if (targetApi < 31) "1.6.0" else "1.9.0"
    addDependency(dependency, minRev = minRev)
  }
}

fun RecipeExecutor.setKotlinVersion(kotlinVersion: String) {
  addClasspathDependency("org.jetbrains.kotlin:kotlin-gradle-plugin:${kotlinVersion}")
}

fun RecipeExecutor.addKotlinIfNeeded(data: ProjectTemplateData, targetApi: Int, noKtx: Boolean = false) {
  if (data.language == Language.Kotlin) {
    setKotlinVersion(data.kotlinVersion)
    applyPlugin("org.jetbrains.kotlin.android", data.kotlinVersion)
    addKotlinDependencies(data.androidXSupport && !noKtx, targetApi)
  }
}
