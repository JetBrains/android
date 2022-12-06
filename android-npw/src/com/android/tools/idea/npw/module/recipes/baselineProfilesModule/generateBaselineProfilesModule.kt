/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.npw.module.recipes.baselineProfilesModule

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.BaselineProfilesMacrobenchmarkCommon.createModule
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.BaselineProfilesMacrobenchmarkCommon.generateBuildVariants
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.BaselineProfilesMacrobenchmarkCommon.runConfigurationGradleTask
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.src.baselineProfileBenchmarksJava
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.src.baselineProfileBenchmarksKt
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.src.baselineProfileGeneratorJava
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.src.baselineProfileGeneratorKt
import com.android.tools.idea.templates.recipe.FindReferencesRecipeExecutor
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.intellij.execution.RunManager
import com.intellij.openapi.module.Module
import org.jetbrains.android.facet.AndroidRootUtil
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import java.io.File

const val GMD_DEVICE = "Pixel 6"
const val GMD_API = 31
const val GENERATOR_CLASS_NAME = "BaselineProfileGenerator"
const val MACROBENCHMARKS_CLASS_NAME = "StartupBenchmarks"
const val BENCHMARKS_CLASS_NAME = "StartupBenchmarks"

const val PROFILE_INSTALLER_MIN_REV = "1.3.0-beta01"
const val BASELINE_PROFILES_PLUGIN_MIN_REV = "1.2.0-SNAPSHOT" // TODO(b/269581369): Need to update prebuilts to the latest public version of the plugin
const val MACROBENCHMARK_MIN_REV = "1.2.0-alpha09"

fun RecipeExecutor.generateBaselineProfilesModule(
  newModule: ModuleTemplateData,
  useGradleKts: Boolean,
  targetModule: Module,
  useGmd: Boolean
) {
  val projectBuildModel = ProjectBuildModel.getOrLog(targetModule.project) ?: return
  val targetModuleAndroidModel = projectBuildModel.getModuleBuildModel(targetModule)?.android() ?: return
  val targetApplicationId = targetModuleAndroidModel.namespace().valueAsString() ?: "com.example.application"

  // TODO(b/269581369): Remove once alpha build of the plugin is released.
  projectBuildModel.projectSettingsModel?.pluginManagement()?.repositories()?.addMavenRepositoryByUrl(
    "https://androidx.dev/snapshots/builds/9605710/artifacts/repository",
    "AndroidX Snapshot Repository"
  )
  projectBuildModel.applyChanges()

  addClasspathDependency("androidx.benchmark:benchmark-baseline-profiles-gradle-plugin:+", BASELINE_PROFILES_PLUGIN_MIN_REV)

  val gmdSpec = if (useGmd) GmdSpec(GMD_DEVICE, GMD_API) else null

  val flavorDimensionNames = targetModuleAndroidModel.flavorDimensions().toList()?.mapNotNull { it.valueAsString() } ?: emptyList()
  val flavorNamesAndDimensions = targetModuleAndroidModel.productFlavors().map {
    FlavorNameAndDimension(it.name(), it.dimension().forceString())
  }

  val variants = generateBuildVariants(flavorDimensionNames, flavorNamesAndDimensions)

  createModule(
    newModule = newModule,
    useGradleKts = useGradleKts,
    macrobenchmarkMinRev = MACROBENCHMARK_MIN_REV,
    buildGradleContent = baselineProfilesBuildGradle(
      newModule = newModule,
      flavorDimensionNames = flavorDimensionNames,
      flavorNamesAndDimensions = flavorNamesAndDimensions,
      useGradleKts = useGradleKts,
      targetModule = targetModule,
      useGmd = gmdSpec,
    ),
    customizeModule = {
      applyPlugin("androidx.baselineprofiles.producer", BASELINE_PROFILES_PLUGIN_MIN_REV)

      createTestClasses(targetModule, newModule, targetApplicationId)
    }
  )

  updateTargetModule(newModule, targetModule)

  setupRunConfigurations(variants, targetModule)
}

fun RecipeExecutor.updateTargetModule(newModule: ModuleTemplateData, targetModule: Module) {
  val targetModuleDir = AndroidRootUtil.getModuleDirPath(targetModule)?.let { File(it) } ?: return

  applyPluginInModule("androidx.baselineprofiles.buildprovider", targetModule, BASELINE_PROFILES_PLUGIN_MIN_REV)
  applyPluginInModule("androidx.baselineprofiles.consumer", targetModule, BASELINE_PROFILES_PLUGIN_MIN_REV)

  addDependency(
    mavenCoordinate = "androidx.profileinstaller:profileinstaller:+",
    configuration = "implementation",
    minRev = PROFILE_INSTALLER_MIN_REV,
    moduleDir = targetModuleDir
  )

  // TODO(b/268476199): Should be renamed to camel case
  addModuleDependency("baselineprofiles", newModule.name, targetModuleDir)
}

/**
 * Add BaselineProfile generator
 * Add StartupBenchmark for measuring effectiveness
 */
fun RecipeExecutor.createTestClasses(
  targetModule: Module,
  moduleData: ModuleTemplateData,
  targetApplicationId: String,
) {
  val language = moduleData.projectTemplateData.language

  val (generatorContent, benchmarksContent) = when (language) {
    Language.Kotlin -> {
      // Create Baseline Profile Generator class
      val generatorContent = baselineProfileGeneratorKt(
        targetModuleName = targetModule.name,
        className = GENERATOR_CLASS_NAME,
        packageName = moduleData.packageName,
        targetPackageName = targetApplicationId,
      )
      // Create Macrobenchmark tests
      val benchmarksContent = baselineProfileBenchmarksKt(
        targetModuleName = targetModule.name,
        className = MACROBENCHMARKS_CLASS_NAME,
        packageName = moduleData.packageName,
        targetPackageName = targetApplicationId,
      )

      generatorContent to benchmarksContent
    }

    Language.Java -> {
      // Create Baseline Profile Generator class
      val generatorContent = baselineProfileGeneratorJava(
        moduleName = moduleData.name,
        className = GENERATOR_CLASS_NAME,
        packageName = moduleData.packageName,
        targetPackageName = targetApplicationId,
      )
      // Create Macrobenchmark tests
      val benchmarksContent = baselineProfileBenchmarksJava(
        targetModuleName = moduleData.name,
        className = MACROBENCHMARKS_CLASS_NAME,
        packageName = moduleData.packageName,
        targetPackageName = targetApplicationId,
      )

      generatorContent to benchmarksContent
    }
  }
  // Save benchmarks + open it
  val benchmarksFile = moduleData.srcDir.resolve("$BENCHMARKS_CLASS_NAME.${language.extension}")
  save(benchmarksContent, benchmarksFile)
  open(benchmarksFile)

  // Save generator + open it
  val generatorFile = moduleData.srcDir.resolve("$GENERATOR_CLASS_NAME.${language.extension}")
  save(generatorContent, generatorFile)
  open(generatorFile)
}

/**
 * Creates run configurations for each build flavor of the target module.
 */
private fun RecipeExecutor.setupRunConfigurations(
  variants: List<String?>,
  targetModule: Module,
) {
  // Only do the actions for the default executor, not when just finding references.
  if (this is FindReferencesRecipeExecutor) return

  val project = targetModule.project

  val runManager = RunManager.getInstance(project)
  val gradleConfigFactory = GradleExternalTaskConfigurationType.getInstance().factory

  variants.forEach { variantName ->
    var runName = "Generate Baseline Profiles"
    if (variants.size > 1) {
      runName += " [$variantName]"
    }

    val runConfig = GradleRunConfiguration(project, gradleConfigFactory, runName).also {
      it.rawCommandLine = runConfigurationGradleTask(
        moduleName = targetModule.getModuleNameForGradleTask(),
        flavorName = variantName,
        filterArgument = BaselineProfilesMacrobenchmarkCommon.FILTER_ARG_BASELINE_PROFILE,
      )
      it.settings.externalProjectPath = project.basePath
    }

    val runConfigSettings = runManager.createConfiguration(runConfig, gradleConfigFactory)
    // Persists in .idea folder
    runConfigSettings.storeInDotIdeaFolder()
    runManager.addConfiguration(runConfigSettings)
  }
}

fun Module.getModuleNameForGradleTask(): String {
  // name of the whole project
  val projectName = project.name

  // module name contains also the whole project name, so we need to remove it, so we can use it to a Gradle task
  return name.removePrefix("$projectName.")
}

