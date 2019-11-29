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
package com.android.tools.idea.npw.module.recipes.thingsModule

import com.android.ide.common.repository.GradleVersion
import com.android.repository.Revision
import com.android.tools.idea.gradle.npw.project.GradleBuildSettings.needsExplicitBuildToolsVersion
import com.android.tools.idea.npw.module.recipes.addKotlinPlugins
import com.android.tools.idea.npw.module.recipes.addKotlinToBaseProject
import com.android.tools.idea.npw.module.recipes.androidModule.buildGradle
import com.android.tools.idea.npw.module.recipes.androidModule.res.values.androidModuleColors
import com.android.tools.idea.npw.module.recipes.androidModule.res.values.androidModuleStrings
import com.android.tools.idea.npw.module.recipes.basicStylesXml
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.npw.module.recipes.generateManifest
import com.android.tools.idea.npw.module.recipes.gitignore
import com.android.tools.idea.npw.module.recipes.proguardRecipe

fun RecipeExecutor.generateTvModule(
  data: ModuleTemplateData,
  appTitle: String? // may be null only for libraries
) {
  val (projectData, srcOut, resOut, manifestOut, testOut, unitTestOut, _, moduleOut) = data
  val language = projectData.language
  val isLibraryProject = data.isLibrary
  val packageName = data.packageName
  val apis = data.apis
  val targetApi = apis.targetApi
  val minApi = apis.minApiLevel

  if (language == Language.Kotlin) {
    addKotlinToBaseProject(language, projectData.kotlinVersion)
  }

  createDirectory(srcOut)
  createDirectory(moduleOut.resolve("libs"))

  addIncludeToSettings(data.name)

  val buildToolsVersion = projectData.buildToolsVersion

  val buildGradle = buildGradle(
    isLibraryProject,
    false,
    packageName,
    apis.buildApiString!!,
    needsExplicitBuildToolsVersion(GradleVersion.parse(projectData.gradlePluginVersion), Revision.parseRevision(buildToolsVersion)),
    buildToolsVersion,
    minApi,
    targetApi,
    projectData.androidXSupport,
    language,
    projectData.gradlePluginVersion
  )
  save(buildGradle, moduleOut.resolve("build.gradle"))

  addDependency("com.android.support.test:runner:+" , "androidTestCompile")
  addDependency("com.android.support.test.espresso:espresso-core:+", "androidTestCompile")
  addDependency("com.google.android.things:androidthings:+", "provided")

  if (language == Language.Kotlin) {
    addKotlinPlugins()
  }

  save(
    generateManifest(packageName, !isLibraryProject),
    manifestOut.resolve("AndroidManifest.xml")
  )

  save(
    gitignore(),
    moduleOut.resolve(".gitignore")
  )

  proguardRecipe(moduleOut, data.isLibrary)

  save(basicStylesXml("android:Theme.Material.Light.DarkActionBar"), resOut.resolve("values/styles.xml"))
  save(androidModuleColors(), resOut.resolve("values/colors.xml"))
  save(androidModuleStrings(appTitle!!), resOut.resolve("values/strings.xml"))

  if (projectData.language == Language.Kotlin && projectData.androidXSupport) {
    addDependency("androidx.core:core-ktx:+")
  }
}
