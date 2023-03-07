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

import com.android.SdkConstants
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel
import com.android.tools.idea.gradle.dsl.api.android.BuildTypeModel
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.BaselineProfilesMacrobenchmarkCommon.createModule
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.BaselineProfilesMacrobenchmarkCommon.getTargetModelProductFlavors
import com.android.tools.idea.npw.module.recipes.macrobenchmarkModule.src.main.exampleMacrobenchmarkJava
import com.android.tools.idea.npw.module.recipes.macrobenchmarkModule.src.main.exampleMacrobenchmarkKt
import com.android.tools.idea.templates.recipe.FindReferencesRecipeExecutor
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.module.Module
import org.jetbrains.android.facet.AndroidRootUtil
import java.io.File

private const val EXAMPLE_BENCHMARK_NAME = "ExampleStartupBenchmark"
private const val BENCHMARK_BUILD_TYPE_NAME = "benchmark"
private const val MACROBENCHMARK_MIN_REV = "1.1.1"

fun RecipeExecutor.generateMacrobenchmarkModule(
  newModule: ModuleTemplateData,
  useGradleKts: Boolean,
  targetModule: Module,
) {
  val projectBuildModel = ProjectBuildModel.getOrLog(targetModule.project)
  val targetModuleAndroidModel = projectBuildModel?.getModuleBuildModel(targetModule)?.android() ?: return
  val targetModuleGradleModel = GradleAndroidModel.get(targetModule) ?: return
  val targetModuleBuildTypes = targetModuleAndroidModel.buildTypes()
  val targetApplicationId = targetModuleAndroidModel.namespace().valueAsString() ?: "com.example.application"
  val benchmarkBuildTypeName = getUniqueBuildTypeName(BENCHMARK_BUILD_TYPE_NAME, targetModuleBuildTypes.map { it.name() })

  val flavors = getTargetModelProductFlavors(targetModuleGradleModel)

  updateTargetModule(
    projectBuildModel = projectBuildModel,
    buildTypeName = benchmarkBuildTypeName,
    targetModule = targetModule,
    targetModuleAndroidModel = targetModuleAndroidModel,
  )

  createModule(
    newModule = newModule,
    useGradleKts = useGradleKts,
    macrobenchmarkMinRev = MACROBENCHMARK_MIN_REV,
    buildGradleContent = macrobenchmarksBuildGradle(
      newModule = newModule,
      useGradleKts = useGradleKts,
      targetModule = targetModule,
      flavors = flavors,
      benchmarkBuildTypeName = benchmarkBuildTypeName,
    ),
    customizeModule = {
      createTestClasses(newModule, targetApplicationId)
    }
  )
}

@VisibleForTesting
fun RecipeExecutor.updateTargetModule(
  projectBuildModel: ProjectBuildModel,
  buildTypeName: String,
  targetModule: Module,
  targetModuleAndroidModel: AndroidModel,
) {
  addBuildTypeToTargetBuildGradle(
    projectBuildModel = projectBuildModel,
    buildTypeName = buildTypeName,
    targetModuleModel = targetModuleAndroidModel,
  )

  val targetModuleDir = AndroidRootUtil.getModuleDirPath(targetModule)?.let { File(it) } ?: return
  addProfileableToTargetManifest(targetModule, targetModuleDir)
}

/**
 * Generates unique build type name with prefix of [buildTypeName] if already exist in [buildTypes]
 */
@VisibleForTesting
fun getUniqueBuildTypeName(buildTypeName: String, buildTypes: List<String>): String {
  var uniqueName = buildTypeName
  var buildTypeSuffix = 0
  while (buildTypes.any { it == uniqueName }) {
    buildTypeSuffix++
    uniqueName = "${buildTypeName}$buildTypeSuffix"
  }

  return uniqueName
}

/**
 * Creates new build type with [buildTypeName] to the specified [targetModuleModel].
 */
@VisibleForTesting
fun RecipeExecutor.addBuildTypeToTargetBuildGradle(
  projectBuildModel: ProjectBuildModel,
  buildTypeName: String,
  targetModuleModel: AndroidModel,
) {
  // Only do the actions for the default executor, not when just finding references.
  if (this is FindReferencesRecipeExecutor) return

  // Release buildType should implicitly exist
  val releaseBuildType: BuildTypeModel = targetModuleModel.buildTypes().first { it.name() == "release" }

  val newBuildType = targetModuleModel.addBuildType(buildTypeName, releaseBuildType)

  // Apply debug signing config for the new buildType
  val debugSigningConfig = targetModuleModel.signingConfigs().firstOrNull { it.name() == "debug" }
  debugSigningConfig?.let {
    val benchmarkSigningConfig = newBuildType.signingConfig()
    benchmarkSigningConfig.setValue(ReferenceTo(debugSigningConfig, benchmarkSigningConfig))
  }

  // Add matchingFallback to release to allow building this buildType in multi-module setup
  val fallback = newBuildType.matchingFallbacks().addListValue()
  fallback?.setValue(releaseBuildType.name())

  newBuildType.debuggable().setValue(false)

  // Apply the buildType to the project
  projectBuildModel.applyChanges()
}

/**
 * TODO(b/269582562): Can be replaced with isProfileable since AGP 8.0
 */
@VisibleForTesting
fun RecipeExecutor.addProfileableToTargetManifest(targetModule: Module, targetModuleRootDir: File) {
  // Only do the actions for the default executor, not when just finding references.
  if (this is FindReferencesRecipeExecutor) return

  val androidModel = com.android.tools.idea.model.AndroidModel.get(targetModule) ?: return
  val targetModuleManifest = File(targetModuleRootDir, "/" + SdkConstants.FN_ANDROID_MANIFEST_XML)
  if (!targetModuleManifest.exists()) return

  // if it's older API, add targetApi flag to the manifest
  val needsTargetFlag = !androidModel.minSdkVersion.isGreaterOrEqualThan(AndroidVersion.VersionCodes.Q)

  mergeXml(appAndroidManifest(needsTargetFlag), targetModuleManifest)
}

@VisibleForTesting
fun RecipeExecutor.createTestClasses(moduleData: ModuleTemplateData, targetApplicationId: String) {
  val language = moduleData.projectTemplateData.language
  val benchmarksContent = when (language) {
    Language.Kotlin -> exampleMacrobenchmarkKt(EXAMPLE_BENCHMARK_NAME, moduleData.packageName, targetApplicationId)
    Language.Java -> exampleMacrobenchmarkJava(EXAMPLE_BENCHMARK_NAME, moduleData.packageName, targetApplicationId)
  }
  val benchmarksFile = moduleData.srcDir.resolve("$EXAMPLE_BENCHMARK_NAME.${language.extension}")
  save(benchmarksContent, benchmarksFile)
  open(benchmarksFile)
}