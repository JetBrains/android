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

import com.android.SdkConstants.FN_ANDROID_MANIFEST_XML
import com.android.tools.idea.npw.module.recipes.addKotlinIfNeeded
import com.android.tools.idea.npw.module.recipes.androidModule.buildGradle
import com.android.tools.idea.npw.module.recipes.androidModule.res.values.androidModuleStrings
import com.android.tools.idea.npw.module.recipes.copyMipmap
import com.android.tools.idea.npw.module.recipes.createDefaultDirectories
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.npw.module.recipes.generateManifest
import com.android.tools.idea.npw.module.recipes.gitignore
import com.android.tools.idea.npw.module.recipes.proguardRecipe

fun RecipeExecutor.generateWearModule(
  data: ModuleTemplateData,
  appTitle: String? // may be null only for libraries
) {
  val (projectData, srcOut, resOut, manifestOut, _, _, _, moduleOut) = data
  val language = projectData.language
  val isLibraryProject = data.isLibrary
  val packageName = data.packageName
  val apis = data.apis
  val targetApi = apis.targetApi
  val minApi = apis.minApiLevel

  createDefaultDirectories(moduleOut, srcOut)
  addIncludeToSettings(data.name)

  save(
    buildGradle(
      isLibraryProject,
      data.baseFeature != null,
      packageName,
      apis.buildApiString!!,
      projectData.buildToolsVersion,
      minApi,
      targetApi,
      projectData.androidXSupport,
      language,
      projectData.gradlePluginVersion
    ),
    moduleOut.resolve("build.gradle")
  )
  // addDependency("com.android.support:appcompat-v7:${buildApi}.+")
  save(generateManifest(packageName, !isLibraryProject), manifestOut.resolve(FN_ANDROID_MANIFEST_XML))
  save(gitignore(), moduleOut.resolve(".gitignore"))
  //addTests(packageName, useAndroidX, isLibraryProject, testOut, unitTestOut, language)
  //addTestDependencies(projectData.gradlePluginVersion)
  proguardRecipe(moduleOut, data.isLibrary)

  copyMipmap(resOut)
  save(androidModuleStrings(appTitle!!), resOut.resolve("values/strings.xml"))

  addKotlinIfNeeded(projectData)

  // Unique to Wear module
  addDependency("com.google.android.support:wearable:+")
  addDependency("com.google.android.gms:play-services-wearable:+")
  addDependency("com.android.support:percent:+")
  addDependency("com.android.support:support-v4:+")
  addDependency("com.android.support:recyclerview-v7:+")
}
