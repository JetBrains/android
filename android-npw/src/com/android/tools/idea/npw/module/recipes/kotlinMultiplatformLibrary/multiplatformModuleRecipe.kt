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
import com.android.ide.common.gradle.Version
import com.android.tools.idea.npw.module.recipes.addInstrumentedTests
import com.android.tools.idea.npw.module.recipes.androidModule.src.exampleUnitTestWithKotlinTest
import com.android.tools.idea.npw.module.recipes.generateManifest
import com.android.tools.idea.npw.module.recipes.gitignore
import com.android.tools.idea.npw.module.recipes.kotlinMultiplatformLibrary.src.exampleAndroidMain
import com.android.tools.idea.npw.module.recipes.kotlinMultiplatformLibrary.src.exampleCommonMain
import com.android.tools.idea.npw.module.recipes.kotlinMultiplatformLibrary.src.exampleIosMain
import com.android.tools.idea.npw.module.recipes.setKotlinVersion
import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import java.io.File

fun RecipeExecutor.generateMultiplatformModule(data: ModuleTemplateData, useKts: Boolean) {

  check(data.category != Category.Compose || data.isCompose) {
    "Template in Compose category must have isCompose set"
  }
  generateModule(data = data, useKts = useKts, manifestXml = generateManifest())
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
      data.name,
      data.namespace,
      data.apis.buildApi.apiString,
      data.apis.minApi.apiString,
    ),
    data.rootDir.resolve(buildFile),
  )

  setKotlinVersion(projectData.kotlinVersion)
  applyPlugin("org.jetbrains.kotlin.multiplatform", projectData.kotlinVersion)
  applyPlugin("com.android.kotlin.multiplatform.library", projectData.agpVersion)

  save(manifestXml, data.manifestDir.resolve(SdkConstants.FN_ANDROID_MANIFEST_XML))
  save(gitignore(), data.rootDir.resolve(".gitignore"))

  addAndroidMain(packageName, data.srcDir, language)
  data.commonSrcDir?.let { dir ->
    addCommonMain(packageName, dir, language)
    addCommonMainDependencies(projectData.kotlinVersion)
    addCommonTestDependencies(projectData.kotlinVersion)
  }
  data.iosSrcDir?.let { addIosMain(packageName, it, language) }

  addMultiplatformLocalTests(packageName, data.unitTestDir)
  addInstrumentedTests(packageName, useAndroidX, isLibraryProject = true, data.testDir, language)
  addInstrumentedTestDependencies()

  addDownloadFromMavenProperty(projectData.kotlinVersion)
}

fun RecipeExecutor.addCommonMainDependencies(kotlinVersion: String) {
  addDependency(
    "org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion",
    "implementation",
    sourceSetName = "commonMain",
  )
}

fun RecipeExecutor.addCommonTestDependencies(kotlinVersion: String) {
  addDependency(
    "org.jetbrains.kotlin:kotlin-test:$kotlinVersion",
    "implementation",
    sourceSetName = "commonTest",
  )
}

fun RecipeExecutor.addInstrumentedTestDependencies() {
  addDependency("androidx.test:runner:+", "implementation", sourceSetName = "androidDeviceTest")
  addDependency("androidx.test:core:+", "implementation", sourceSetName = "androidDeviceTest")
  addDependency("androidx.test.ext:junit:+", "implementation", sourceSetName = "androidDeviceTest")
}

fun RecipeExecutor.addAndroidMain(packageName: String, outFolder: File, language: Language) {
  val ext = language.extension
  save(exampleAndroidMain(packageName), outFolder.resolve("Platform.android.$ext"))
}

fun RecipeExecutor.addCommonMain(packageName: String, outFolder: File, language: Language) {
  val ext = language.extension
  save(exampleCommonMain(packageName), outFolder.resolve("Platform.$ext"))
}

fun RecipeExecutor.addIosMain(packageName: String, outFolder: File, language: Language) {
  val ext = language.extension
  save(exampleIosMain(packageName), outFolder.resolve("Platform.ios.$ext"))
}

fun RecipeExecutor.addMultiplatformLocalTests(packageName: String, localTestOut: File) {
  save(exampleUnitTestWithKotlinTest(packageName), localTestOut.resolve("ExampleUnitTest.kt"))
}

/**
 * Add kotlin.native.distribution.downloadFromMaven=true to gradle.properties if it doesn't already
 * exist, and if the Kotlin version is lower than 2.0.0
 */
fun RecipeExecutor.addDownloadFromMavenProperty(kotlinVersion: String) {
  if (Version.parse(kotlinVersion) >= Version.parse("2.0.0")) {
    return
  }
  addProjectGradleProperty(
    "kotlin.native.distribution.downloadFromMaven",
    """# Opt in to the future (Kotlin Gradle plugin 2.0.0) default behavior of downloading the
       # Kotlin native libraries from Maven.
      kotlin.native.distribution.downloadFromMaven=true
      """
      .trimIndent(),
  )
}
