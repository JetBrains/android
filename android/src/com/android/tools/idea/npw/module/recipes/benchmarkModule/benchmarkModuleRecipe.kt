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
package com.android.tools.idea.npw.module.recipes.benchmarkModule

import com.android.SdkConstants.FN_BUILD_GRADLE
import com.android.SdkConstants.FN_BUILD_GRADLE_KTS
import com.android.ide.common.repository.GradleVersion
import com.android.repository.Revision
import com.android.tools.idea.gradle.npw.project.GradleBuildSettings.needsExplicitBuildToolsVersion
import com.android.tools.idea.npw.module.recipes.addKotlinIfNeeded
import com.android.tools.idea.npw.module.recipes.benchmarkModule.src.androidTest.exampleBenchmarkJava
import com.android.tools.idea.npw.module.recipes.benchmarkModule.src.androidTest.exampleBenchmarkKt
import com.android.tools.idea.npw.module.recipes.benchmarkModule.src.main.androidManifestXml
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.npw.module.recipes.benchmarkModule.src.androidTest.androidManifestXml as testAndroidManifestXml

fun RecipeExecutor.generateBenchmarkModule(
  moduleData: ModuleTemplateData,
  useGradleKts: Boolean
) {
  val projectData = moduleData.projectTemplateData
  val testOut = moduleData.testDir
  val packageName = moduleData.packageName
  val moduleOut = moduleData.rootDir
  val buildToolsVersion = projectData.buildToolsVersion
  val (buildApi, targetApi,  minApi) = moduleData.apis
  val language = projectData.language

  addClasspathDependency("androidx.benchmark:benchmark-gradle-plugin:+")

  addIncludeToSettings(moduleData.name)
  save(benchmarkProguardRules(), moduleOut.resolve("benchmark-proguard-rules.pro"))

  val bg = buildGradle(
    needsExplicitBuildToolsVersion(GradleVersion.parse(projectData.gradlePluginVersion), Revision.parseRevision(buildToolsVersion)),
    buildApi.apiString,
    buildToolsVersion,
    minApi.apiString,
    targetApi.apiString,
    language,
    projectData.gradlePluginVersion,
    useGradleKts
  )
  val buildFile = if (useGradleKts) FN_BUILD_GRADLE_KTS else FN_BUILD_GRADLE

  save(bg, moduleOut.resolve(buildFile))
  applyPlugin("com.android.library")
  applyPlugin("androidx.benchmark")
  addDependency("androidx.test:runner:+", "androidTestImplementation")
  addDependency("androidx.test.ext:junit:+", "androidTestImplementation")
  addDependency("junit:junit:4.+", "androidTestImplementation")
  addDependency("androidx.benchmark:benchmark-junit4:+", "androidTestImplementation")

  save(androidManifestXml(packageName), moduleOut.resolve("src/main/AndroidManifest.xml"))
  save(testAndroidManifestXml(packageName), moduleOut.resolve("src/androidTest/AndroidManifest.xml"))

  if (language == Language.Kotlin) {
    save(exampleBenchmarkKt(packageName), testOut.resolve("ExampleBenchmark.kt"))
  }
  else {
    save(exampleBenchmarkJava(packageName), testOut.resolve("ExampleBenchmark.java"))
  }

  addKotlinIfNeeded(projectData, noKtx = true)
}
