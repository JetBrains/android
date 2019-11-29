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
package com.android.tools.idea.npw.module.recipes.androidModule

import com.android.ide.common.repository.GradleVersion
import com.android.repository.Revision
import com.android.tools.idea.gradle.npw.project.GradleBuildSettings.needsExplicitBuildToolsVersion
import com.android.tools.idea.npw.module.recipes.addKotlinPlugins
import com.android.tools.idea.npw.module.recipes.addKotlinToBaseProject
import com.android.tools.idea.npw.module.recipes.androidModule.res.values.androidModuleColors
import com.android.tools.idea.npw.module.recipes.androidModule.res.values.androidModuleStrings
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.npw.module.recipes.androidModule.res.values.androidModuleStyles
import com.android.tools.idea.npw.module.recipes.androidModule.src.exampleInstrumentedTestJava
import com.android.tools.idea.npw.module.recipes.androidModule.src.exampleInstrumentedTestKt
import com.android.tools.idea.npw.module.recipes.androidModule.src.exampleUnitTestJava
import com.android.tools.idea.npw.module.recipes.androidModule.src.exampleUnitTestKt
import com.android.tools.idea.npw.module.recipes.copyIcons
import com.android.tools.idea.npw.module.recipes.generateManifest
import com.android.tools.idea.npw.module.recipes.gitignore
import com.android.tools.idea.npw.module.recipes.proguardRecipe
import com.android.tools.idea.wizard.template.FormFactor

fun RecipeExecutor.generateAndroidModule(
  data: ModuleTemplateData,
  appTitle: String?, // may be null only for libraries
  includeCppSupport: Boolean = false,
  cppFlags: String = ""
) {
  val (projectData, srcOut, resOut, manifestOut, testOut, unitTestOut, _, moduleOut) = data
  val language = projectData.language
  val isLibraryProject = data.isLibrary
  val packageName = data.packageName
  val topOut = projectData.rootDir
  val apis = data.apis
  val buildApi = apis.buildApi!!
  val targetApi = apis.targetApi
  val minApi = apis.minApiLevel
  val useAndroidX = projectData.androidXSupport

  createDirectory(moduleOut.resolve("libs"))
  createDirectory(resOut.resolve("drawable"))
  createDirectory(srcOut)
  createDirectory(unitTestOut)

  addIncludeToSettings(data.name)

  val buildToolsVersion = projectData.buildToolsVersion
  val supportsImprovedTestDeps = GradleVersion.parse(projectData.gradlePluginVersion).compareIgnoringQualifiers("3.0.0") >= 0
  val buildGradle = buildGradle(
    isLibraryProject,
    data.baseFeature != null,
    packageName,
    apis.buildApiString!!,
    needsExplicitBuildToolsVersion(GradleVersion.parse(projectData.gradlePluginVersion), Revision.parseRevision(buildToolsVersion)),
    buildToolsVersion,
    minApi,
    targetApi,
    projectData.androidXSupport,
    language,
    projectData.gradlePluginVersion,
    supportsImprovedTestDeps,
    includeCppSupport,
    cppFlags
  )

  save(buildGradle, moduleOut.resolve("build.gradle"))

  addKotlinToBaseProject(language, projectData.kotlinVersion)
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

  if (!isLibraryProject) {
    save(androidModuleStrings(appTitle!!), resOut.resolve("values/strings.xml"))
  }
  save(
    if (language == Language.Kotlin)
      exampleInstrumentedTestKt(packageName, useAndroidX, isLibraryProject)
    else
      exampleInstrumentedTestJava(packageName, useAndroidX, isLibraryProject) ,
    testOut.resolve("ExampleInstrumentedTest.${language.extension}")
  )
  save(
    if (language == Language.Kotlin)
      exampleUnitTestKt(packageName)
    else
      exampleUnitTestJava(packageName),
    unitTestOut.resolve("ExampleUnitTest.${language.extension}")
  )
  addDependency("junit:junit:4.12", "testCompile")

  if (supportsImprovedTestDeps) {
    addDependency("com.android.support.test:runner:+", "androidTestCompile")
    addDependency("com.android.support.test.espresso:espresso-core:+", "androidTestCompile")
  }

  addDependency("com.android.support:appcompat-v7:${buildApi}.+")

  proguardRecipe(moduleOut, data.isLibrary)

  if (!isLibraryProject) {
    copyIcons(resOut)
  }

  if (!isLibraryProject) {
    save(androidModuleStyles(), resOut.resolve("values/styles.xml"))
    save(androidModuleColors(), resOut.resolve("values/colors.xml"))
  }

  if (includeCppSupport) {
    val nativeSrcOut = moduleOut.resolve("src/main/cpp")
    createDirectory(nativeSrcOut)

    save(cMakeListsTxt(), nativeSrcOut.resolve("CMakeLists.txt"))
  }

  if (projectData.hasFormFactor(FormFactor.Mobile) && projectData.hasFormFactor(FormFactor.Wear)) {
    addDependency("com.google.android.gms:play-services-wearable:+", "compile")
  }

  if (projectData.language == Language.Kotlin && projectData.androidXSupport) {
    addDependency("androidx.core:core-ktx:+")
  }
}
