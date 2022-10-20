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
package com.android.tools.idea.npw.module.recipes.macrobenchmarkModule

import com.android.SdkConstants.FN_BUILD_GRADLE
import com.android.SdkConstants.FN_BUILD_GRADLE_KTS
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.npw.module.recipes.addKotlinIfNeeded
import com.android.tools.idea.npw.module.recipes.gitignore
import com.android.tools.idea.npw.module.recipes.macrobenchmarkModule.src.main.androidManifestXml
import com.android.tools.idea.npw.module.recipes.macrobenchmarkModule.src.main.exampleMacrobenchmarkJava
import com.android.tools.idea.npw.module.recipes.macrobenchmarkModule.src.main.exampleMacrobenchmarkKt
import com.android.tools.idea.templates.recipe.DefaultRecipeExecutor
import com.android.tools.idea.util.androidFacet
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.intellij.openapi.module.Module
import org.jetbrains.android.facet.AndroidRootUtil
import java.io.File

private const val minRev = "1.1.0-beta04"
private const val exampleBenchmarkName = "ExampleStartupBenchmark"

fun RecipeExecutor.generateMacrobenchmarkModule(
  moduleData: ModuleTemplateData,
  useGradleKts: Boolean,
  targetModule: Module,
) {
  val projectData = moduleData.projectTemplateData
  val srcOut = moduleData.srcDir
  val packageName = moduleData.packageName
  val moduleOut = moduleData.rootDir
  val (buildApi, targetApi, minApi) = moduleData.apis
  val language = projectData.language

  var benchmarkBuildTypeName = "benchmark"
  if (this is DefaultRecipeExecutor) {
    benchmarkBuildTypeName = generateUniqueBenchmarkBuildTypeName(targetModule = targetModule)
    targetModule.addBuildType(name = benchmarkBuildTypeName, debuggable = false)
    addProfileableToTargetManifest(targetModule)
  }

  val targetApplicationId =
    AndroidModel.get(targetModule)?.applicationId?.takeUnless { it == AndroidModel.UNINITIALIZED_APPLICATION_ID }
    ?: "com.example.application"


  addIncludeToSettings(moduleData.name)

  val bg = buildGradle(
    packageName = packageName,
    buildApiString = buildApi.apiString,
    minApi = minApi.apiString,
    targetApiString = targetApi.apiString,
    language = language,
    gradlePluginVersion = projectData.gradlePluginVersion,
    useGradleKts = useGradleKts,
    targetModule = targetModule,
    benchmarkBuildTypeName = benchmarkBuildTypeName,
  )
  val buildFile = if (useGradleKts) FN_BUILD_GRADLE_KTS else FN_BUILD_GRADLE

  save(bg, moduleOut.resolve(buildFile))
  applyPlugin("com.android.test", projectData.gradlePluginVersion)

  addDependency("androidx.test.ext:junit:+", "implementation")
  addDependency("androidx.test.espresso:espresso-core:3.+", "implementation")
  addDependency("androidx.test.uiautomator:uiautomator:2.+", "implementation")
  addDependency("androidx.benchmark:benchmark-macro-junit4:+", "implementation", minRev)

  save(androidManifestXml(targetApplicationId), moduleOut.resolve("src/main/AndroidManifest.xml"))
  save(gitignore(), moduleOut.resolve(".gitignore"))

  if (language == Language.Kotlin) {
    save(exampleMacrobenchmarkKt(exampleBenchmarkName, packageName, targetApplicationId), srcOut.resolve("$exampleBenchmarkName.kt"))
    open(srcOut.resolve("$exampleBenchmarkName.kt"))
  }
  else {
    save(exampleMacrobenchmarkJava(exampleBenchmarkName, packageName, targetApplicationId), srcOut.resolve("$exampleBenchmarkName.java"))
    open(srcOut.resolve("$exampleBenchmarkName.java"))
  }

  addKotlinIfNeeded(projectData, targetApi = targetApi.api, noKtx = true)
}

/**
 * Generate unique name for a new benchmark build type that this macrobenchmark module will be setup with.
 *
 * @param targetModule The existing target app module which the new buildType should not collide names with.
 */
private fun generateUniqueBenchmarkBuildTypeName(targetModule: Module): String {
  val projectBuildModel = ProjectBuildModel.getOrLog(targetModule.project) ?: return "benchmark"
  val androidBuildModel = projectBuildModel.getModuleBuildModel(targetModule)?.android() ?: return "benchmark"

  var benchmarkBuildTypeSuffix: Int? = null
  var benchmarkBuildTypeName = "benchmark${benchmarkBuildTypeSuffix ?: ""}"
  while (androidBuildModel.signingConfigs().any { it.name() == benchmarkBuildTypeName }) {
    benchmarkBuildTypeSuffix = benchmarkBuildTypeSuffix?.inc() ?: 1
    benchmarkBuildTypeName = "benchmark${benchmarkBuildTypeSuffix}"
  }

  return benchmarkBuildTypeName
}

private fun RecipeExecutor.addProfileableToTargetManifest(targetModule: Module) {
  val androidModel = AndroidModel.get(targetModule) ?: return
  val androidFacet = targetModule.androidFacet ?: return
  val targetModuleRootDir = AndroidRootUtil.getModuleDirPath(targetModule) ?: return
  val targetModuleManifest = File(targetModuleRootDir, androidFacet.properties.MANIFEST_FILE_RELATIVE_PATH)
  if (!targetModuleManifest.exists()) return

  // if it's older API, add targetApi flag to the manifest
  val needsTargetFlag = !androidModel.minSdkVersion.isGreaterOrEqualThan(AndroidVersion.VersionCodes.Q)

  mergeXml(appAndroidManifest(needsTargetFlag), targetModuleManifest)
}

private fun Module.addBuildType(name: String, debuggable: Boolean) {
  val projectBuildModel = ProjectBuildModel.getOrLog(project) ?: return
  val androidBuildModel = projectBuildModel.getModuleBuildModel(this)?.android() ?: return

  val benchmarkBuildType = androidBuildModel.addBuildType(name)

  // apply debug signingConfig
  val debugSignConfigBuildModel = androidBuildModel.signingConfigs().firstOrNull { it.name() == "debug" }
  debugSignConfigBuildModel?.let {
    val benchmarkSigningConfigModel = benchmarkBuildType.signingConfig()
    benchmarkSigningConfigModel.setValue(ReferenceTo(debugSignConfigBuildModel, benchmarkSigningConfigModel))
  }

  // add matchingFallback to release to allow building benchmark buildType in multi-module setup
  val existingMatchingFallbacks = benchmarkBuildType.matchingFallbacks().toList()
  val hasReleaseFallback = existingMatchingFallbacks?.any { it.valueAsString() == "release" } ?: false
  if (!hasReleaseFallback) {
    val fallback = benchmarkBuildType.matchingFallbacks().addListValue()
    fallback?.setValue("release")
  }

  // set debuggable
  benchmarkBuildType.debuggable().setValue(debuggable)

  projectBuildModel.applyChanges()
}
