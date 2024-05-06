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

import com.android.SdkConstants
import com.android.SdkConstants.FN_BUILD_GRADLE
import com.android.tools.idea.npw.module.recipes.addKotlinDependencies
import com.android.tools.idea.npw.module.recipes.gitignore
import com.android.tools.idea.npw.module.recipes.pureLibrary.src.placeholderJava
import com.android.tools.idea.npw.module.recipes.pureLibrary.src.placeholderKt
import com.android.tools.idea.npw.module.recipes.setKotlinVersion
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor

fun RecipeExecutor.generatePureLibrary(
  moduleData: ModuleTemplateData,
  className: String,
  useGradleKts: Boolean,
  useVersionCatalog: Boolean = true,
) {
  val (projectData, srcOut) = moduleData
  val moduleOut = moduleData.rootDir
  val language = projectData.language
  val packageName = moduleData.packageName

  addIncludeToSettings(moduleData.name)

  val buildFile = if (useGradleKts) SdkConstants.FN_BUILD_GRADLE_KTS else FN_BUILD_GRADLE
  save(buildGradle(getJavaVersion(), isKts = useGradleKts, useVersionCatalog = useVersionCatalog), moduleOut.resolve(buildFile))
  applyPlugin("java-library", null)
  save(
    if (language == Language.Kotlin) placeholderKt(packageName, className) else placeholderJava(packageName, className),
    srcOut.resolve("$className.${language.extension}")
  )

  save(
    gitignore(),
    moduleOut.resolve(".gitignore")
  )

  if (language == Language.Kotlin) {
    setKotlinVersion(projectData.kotlinVersion)
    addKotlinDependencies(androidX = false, targetApi = moduleData.apis.targetApi.api)
    applyPlugin("org.jetbrains.kotlin.jvm", projectData.kotlinVersion)
  }
}
