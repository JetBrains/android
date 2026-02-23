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

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.npw.module.recipes.addKotlinIfNeeded
import com.android.tools.idea.npw.module.recipes.benchmarkModule.src.androidTest.androidManifestXml as testAndroidManifestXml
import com.android.tools.idea.npw.module.recipes.benchmarkModule.src.androidTest.exampleBenchmarkJava
import com.android.tools.idea.npw.module.recipes.benchmarkModule.src.androidTest.exampleBenchmarkKt
import com.android.tools.idea.npw.module.recipes.benchmarkModule.src.main.androidManifestXml
import com.android.tools.idea.npw.module.recipes.gitignore
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor

private const val minRev = "1.2.4"
private const val exampleBenchmarkName = "ExampleBenchmark"

fun RecipeExecutor.generateBenchmarkModule(moduleData: ModuleTemplateData, version: AgpVersion) {
  val projectData = moduleData.projectTemplateData
  val dslLanguage = projectData.dslLanguage
  val testOut = moduleData.testDir
  val packageName = moduleData.packageName
  val moduleOut = moduleData.rootDir
  val (buildApi, targetApi, minApi) = moduleData.apis
  val language = projectData.language

  addClasspathDependency("androidx.benchmark:benchmark-gradle-plugin:+", minRev)

  addIncludeToSettings(moduleData.name)

  if (version < AgpVersion.parse("9.0.0")) {
    save(benchmarkProguardRules(), moduleOut.resolve("benchmark-proguard-rules.pro"))
  } else {
    val aarKeepRulesFolder = moduleOut.resolve("src/main/aarKeepRules")
    aarKeepRulesFolder.mkdirs()
    save(benchmarkKeepRules(), aarKeepRulesFolder.resolve("benchmark-rules.keep"))
  }

  val bg =
    buildGradle(
      packageName = packageName,
      minApi = minApi,
      targetApi = targetApi,
      agpVersion = projectData.agpVersion,
      dslLanguage = dslLanguage,
    )
  val buildFile = dslLanguage.buildFileName

  save(bg, moduleOut.resolve(buildFile))
  addCompileSdk(buildApi)
  addPlugin("com.android.library", "com.android.tools.build:gradle", projectData.agpVersion.toString())
  addPlugin("androidx.benchmark", "androidx.benchmark:benchmark-baseline-profile-gradle-plugin", minRev)
  addDependency("androidx.test:runner:+", "androidTestImplementation")
  addDependency("androidx.test.ext:junit:+", "androidTestImplementation")
  addDependency("junit:junit:4.+", "androidTestImplementation", "4.13.2")
  addDependency("androidx.benchmark:benchmark-junit4:+", "androidTestImplementation", minRev)

  save(androidManifestXml(), moduleOut.resolve("src/main/AndroidManifest.xml"))
  save(testAndroidManifestXml(), moduleOut.resolve("src/androidTest/AndroidManifest.xml"))
  save(gitignore(), moduleOut.resolve(".gitignore"))

  if (language == Language.Kotlin) {
    save(exampleBenchmarkKt(exampleBenchmarkName, packageName), testOut.resolve("$exampleBenchmarkName.kt"))
  } else {
    save(exampleBenchmarkJava(exampleBenchmarkName, packageName), testOut.resolve("$exampleBenchmarkName.java"))
  }

  addKotlinIfNeeded(projectData, targetApi = targetApi.apiLevel, noKtx = true)
  setJavaKotlinCompileOptions(language == Language.Kotlin)
}
