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
import org.jetbrains.kotlin.config.ApiVersion

fun RecipeExecutor.addKotlinDependencies(androidX: Boolean, targetApi: Int) {
  if (androidX) {
    val dependency =
      if (targetApi < 31) "androidx.core:core-ktx:1.6.+" else "androidx.core:core-ktx:+"
    val minRev = if (targetApi < 31) "1.6.0" else "1.9.0"
    addDependency(dependency, minRev = minRev)
  }
}

fun RecipeExecutor.setKotlinVersion(kotlinVersion: String) {
  addClasspathDependency("org.jetbrains.kotlin:kotlin-gradle-plugin:${kotlinVersion}")
}

fun RecipeExecutor.addKotlinIfNeeded(
  data: ProjectTemplateData,
  targetApi: Int,
  noKtx: Boolean = false,
) {
  if (data.language == Language.Kotlin) {
    setKotlinVersion(data.kotlinVersion)
    applyPlugin("org.jetbrains.kotlin.android", data.kotlinVersion)
    addKotlinDependencies(data.androidXSupport && !noKtx, targetApi)

    val kotlinVersion = ApiVersion.parse(data.kotlinVersion)
    if (kotlinVersion != null && kotlinVersion < ApiVersion.KOTLIN_1_8) {
      // This is to avoid the class duplication from different kotlin-stdlib versions
      // See
      // https://kotlinlang.org/docs/whatsnew18.html#usage-of-the-latest-kotlin-stdlib-version-in-transitive-dependencies
      // for more details.
      // Adding this solves duplicated classes issues in kotlin-stdlib in following possible
      // scenario
      // 1. - When Kotlin gradle plugin < 1.8 (e.g. 1.7.20)
      //    - When the app has transitive dependency to kotlin-stdlib:1.8.0 (e.g.
      // "androidx.lifecycle:lifecycle-livedata-ktx:2.6.1")
      addPlatformDependency("org.jetbrains.kotlin:kotlin-bom:1.8.0")
    }
  }
}
