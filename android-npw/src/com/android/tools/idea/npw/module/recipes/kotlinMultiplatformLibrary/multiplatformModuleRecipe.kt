/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.npw.module.recipes.kotlinMultiplatformLibrary

import com.android.SdkConstants
import com.android.tools.idea.npw.module.recipes.addInstrumentedTests
import com.android.tools.idea.npw.module.recipes.androidModule.src.exampleUnitTestWithKotlinTest
import com.android.tools.idea.npw.module.recipes.generateManifest
import com.android.tools.idea.npw.module.recipes.gitignore
import com.android.tools.idea.npw.module.recipes.kotlinMultiplatformLibrary.src.exampleAndroidMain
import com.android.tools.idea.npw.module.recipes.kotlinMultiplatformLibrary.src.exampleCommonMain
import com.android.tools.idea.npw.module.recipes.setKotlinVersion
import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import java.io.File

fun RecipeExecutor.generateMultiplatformModule(
  data: ModuleTemplateData,
  useKts: Boolean,
) {

  check(data.category != Category.Compose || data.isCompose) { "Template in Compose category must have isCompose set" }
  generateModule(
    data = data,
    useKts = useKts,
    manifestXml = generateManifest(),
  )
}

private fun RecipeExecutor.generateModule(
  data: ModuleTemplateData,
  useKts: Boolean,
  manifestXml: String,
) {
  val projectData = data.projectTemplateData
  val language = projectData.language
  val packageName = data.packageName
  val useAndroidX = data.projectTemplateData.androidXSupport

  createDirectory(data.srcDir)
  addIncludeToSettings(data.name)

  val buildFile = if (useKts) SdkConstants.FN_BUILD_GRADLE_KTS else SdkConstants.FN_BUILD_GRADLE

  save(
    buildKmpGradle(
      projectData.agpVersion,
      data.namespace,
      data.apis.buildApi.apiString,
      data.apis.minApi.apiString,
    ),
    data.rootDir.resolve(buildFile)
  )

  setKotlinVersion(projectData.kotlinVersion)
  applyPlugin("org.jetbrains.kotlin.multiplatform", projectData.kotlinVersion)
  applyPlugin("com.android.kotlin.multiplatform.library", projectData.agpVersion)

  save(manifestXml, data.manifestDir.resolve(SdkConstants.FN_ANDROID_MANIFEST_XML))
  save(gitignore(), data.rootDir.resolve(".gitignore"))

  addAndroidMain(packageName, data.srcDir, language)
  data.commonSrcDir?.let { dir ->
    addCommonMain(packageName, dir, language)
    addCommonTestDependencies(projectData.kotlinVersion)
  }

  addMultiplatformLocalTests(packageName, data.unitTestDir)
  addInstrumentedTests(packageName, useAndroidX, false, data.testDir, language)
  addInstrumentedTestDependencies()
}

fun RecipeExecutor.addCommonTestDependencies(kotlinVersion: String) {
  addDependency("org.jetbrains.kotlin:kotlin-test:+", "implementation", minRev = kotlinVersion, sourceSetName = "commonTest")
}

fun RecipeExecutor.addInstrumentedTestDependencies() {
  addDependency("androidx.test:runner:+", "implementation", sourceSetName = "androidInstrumentedTest")
  addDependency("androidx.test:core:+", "implementation", sourceSetName = "androidInstrumentedTest")
  addDependency("androidx.test.ext:junit:+", "implementation", sourceSetName = "androidInstrumentedTest")
}

fun RecipeExecutor.addAndroidMain(
  packageName: String, outFolder: File, language: Language
) {
  val ext = language.extension
  save(
    exampleAndroidMain(packageName),
    outFolder.resolve("AndroidPlatform.$ext")
  )
}

fun RecipeExecutor.addCommonMain(
  packageName: String, outFolder: File, language: Language
) {
  val ext = language.extension
  save(
    exampleCommonMain(packageName),
    outFolder.resolve("Platform.$ext")
  )
}

fun RecipeExecutor.addMultiplatformLocalTests(
  packageName: String, localTestOut: File
) {
  save(
    exampleUnitTestWithKotlinTest(packageName),
    localTestOut.resolve("ExampleUnitTest.kt")
  )
}