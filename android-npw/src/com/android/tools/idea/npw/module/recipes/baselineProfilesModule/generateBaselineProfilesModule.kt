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

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.BaselineProfilesMacrobenchmarkCommon.FILTER_ARG_BASELINE_PROFILE
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.BaselineProfilesMacrobenchmarkCommon.FILTER_ARG_MACROBENCHMARK
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.BaselineProfilesMacrobenchmarkCommon.createModule
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.BaselineProfilesMacrobenchmarkCommon.getTargetModelProductFlavors
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.src.baselineProfileBenchmarksJava
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.src.baselineProfileBenchmarksKt
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.src.baselineProfileGeneratorJava
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.src.baselineProfileGeneratorKt
import com.android.tools.idea.run.configuration.AndroidBaselineProfileRunConfiguration
import com.android.tools.idea.run.configuration.AndroidBaselineProfileRunConfigurationType
import com.android.tools.idea.run.configuration.BP_PLUGIN_FILTERING_SUPPORTED
import com.android.tools.idea.templates.recipe.FindReferencesRecipeExecutor
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.google.common.annotations.VisibleForTesting
import com.intellij.execution.RunManager
import com.intellij.openapi.module.Module
import org.jetbrains.android.facet.AndroidRootUtil
import org.jetbrains.kotlin.idea.gradleTooling.capitalize
import java.io.File

const val GMD_DEVICE = "Pixel 6"
const val GMD_API = 34
const val GMD_SYSTEM_IMAGE_SOURCE = "google"
const val GENERATOR_CLASS_NAME = "BaselineProfileGenerator"
const val MACROBENCHMARKS_CLASS_NAME = "StartupBenchmarks"
const val BENCHMARKS_CLASS_NAME = "StartupBenchmarks"
const val RUN_CONFIGURATION_NAME = "Generate Baseline Profile"
const val PROFILE_INSTALLER_MIN_REV = "1.3.1"
const val BASELINE_PROFILES_PLUGIN_MIN_REV = "1.2.0"
const val MACROBENCHMARK_MIN_REV = "1.2.0"

fun RecipeExecutor.generateBaselineProfilesModule(
  newModule: ModuleTemplateData,
  useGradleKts: Boolean,
  targetModule: Module,
  useGmd: Boolean,
  useVersionCatalog: Boolean
) {
  val targetModuleGradleModel = GradleAndroidModel.get(targetModule) ?: return
  val targetApplicationId = targetModuleGradleModel.applicationId

  addClasspathDependency("androidx.benchmark:benchmark-baseline-profile-gradle-plugin:+", BASELINE_PROFILES_PLUGIN_MIN_REV)

  val gmdSpec = if (useGmd) GmdSpec(GMD_DEVICE, GMD_API, GMD_SYSTEM_IMAGE_SOURCE) else null

  val flavors = getTargetModelProductFlavors(targetModuleGradleModel)

  createModule(
    newModule = newModule,
    useGradleKts = useGradleKts,
    macrobenchmarkMinRev = MACROBENCHMARK_MIN_REV,
    buildGradleContent = baselineProfilesBuildGradle(
      newModule = newModule,
      flavors = flavors,
      useGradleKts = useGradleKts,
      targetModule = targetModule,
      useGmd = gmdSpec,
      useVersionCatalog = useVersionCatalog
    ),
    customizeModule = {
      applyPlugin("androidx.baselineprofile", BASELINE_PROFILES_PLUGIN_MIN_REV)

      createTestClasses(
        targetModule = targetModule,
        newModule = newModule,
        targetApplicationId = targetApplicationId
      )
    }
  )

  updateTargetModule(newModule, targetModule)

  // Only do the actions for the default executor, not when just finding references.
  if (this !is FindReferencesRecipeExecutor) {
    setupRunConfigurations(targetModule)
  }
}

@VisibleForTesting
fun RecipeExecutor.updateTargetModule(newModule: ModuleTemplateData, targetModule: Module) {
  val targetModuleDir = AndroidRootUtil.getModuleDirPath(targetModule)?.let { File(it) } ?: return

  // This needs to be added, because many projects don't define this plugin in plugins { } block, and therefore it fails with unknown version.
  applyPluginInModule("com.android.application", targetModule, newModule.projectTemplateData.agpVersion)

  applyPluginInModule("androidx.baselineprofile", targetModule, BASELINE_PROFILES_PLUGIN_MIN_REV)

  addDependency(
    mavenCoordinate = "androidx.profileinstaller:profileinstaller:+",
    configuration = "implementation",
    minRev = PROFILE_INSTALLER_MIN_REV,
    moduleDir = targetModuleDir
  )

  addModuleDependency("baselineProfile", newModule.name, targetModuleDir)
}

/**
 * Add BaselineProfile generator
 * Add StartupBenchmark for measuring effectiveness
 */
@VisibleForTesting
fun RecipeExecutor.createTestClasses(
  targetModule: Module,
  newModule: ModuleTemplateData,
  targetApplicationId: String,
) {
  val language = newModule.projectTemplateData.language
  val pluginTaskName = baselineProfileTaskName("release")

  val (generatorContent, benchmarksContent) = when (language) {
    Language.Kotlin -> {
      // Create Baseline Profile Generator class
      val generatorContent = baselineProfileGeneratorKt(
        targetModuleName = targetModule.getModuleNameForGradleTask(),
        pluginTaskName = pluginTaskName,
        className = GENERATOR_CLASS_NAME,
        packageName = newModule.packageName,
        targetPackageName = targetApplicationId,
      )
      // Create Macrobenchmark tests
      val benchmarksContent = baselineProfileBenchmarksKt(
        newModuleName = newModule.name,
        className = MACROBENCHMARKS_CLASS_NAME,
        packageName = newModule.packageName,
        targetPackageName = targetApplicationId,
      )

      generatorContent to benchmarksContent
    }

    Language.Java -> {
      // Create Baseline Profile Generator class
      val generatorContent = baselineProfileGeneratorJava(
        targetModuleName = targetModule.getModuleNameForGradleTask(),
        pluginTaskName = pluginTaskName,
        className = GENERATOR_CLASS_NAME,
        packageName = newModule.packageName,
        targetPackageName = targetApplicationId,
      )
      // Create Macrobenchmark tests
      val benchmarksContent = baselineProfileBenchmarksJava(
        newModuleName = newModule.name,
        className = MACROBENCHMARKS_CLASS_NAME,
        packageName = newModule.packageName,
        targetPackageName = targetApplicationId,
      )

      generatorContent to benchmarksContent
    }
  }
  // Save benchmarks + open it
  val benchmarksFile = newModule.srcDir.resolve("$BENCHMARKS_CLASS_NAME.${language.extension}")
  save(benchmarksContent, benchmarksFile)
  open(benchmarksFile)

  // Save generator + open it
  val generatorFile = newModule.srcDir.resolve("$GENERATOR_CLASS_NAME.${language.extension}")
  save(generatorContent, generatorFile)
  open(generatorFile)
}

/**
 * Creates run configurations for each build flavor of the target module.
 */
@VisibleForTesting
fun setupRunConfigurations(
  targetModule: Module,
  runManager: RunManager = RunManager.getInstance(targetModule.project)
) {
  val project = targetModule.project

  val configFactory = AndroidBaselineProfileRunConfigurationType.getInstance().factory

  val runConfigForSelected = AndroidBaselineProfileRunConfiguration(project, configFactory, RUN_CONFIGURATION_NAME).apply {
    setModule(targetModule)
  }
  val runConfigSettingsForSelected = runManager.createConfiguration(runConfigForSelected, configFactory)
  // Persists in .idea folder
  runConfigSettingsForSelected.storeInDotIdeaFolder()
  runManager.addConfiguration(runConfigSettingsForSelected)
  runManager.selectedConfiguration = runConfigSettingsForSelected
}

@VisibleForTesting
fun Module.getModuleNameForGradleTask(): String {
  // name of the whole project replacing spaces, because that's not allowed for Gradle modules
  val projectName = project.name.replace(' ', '_')

  // module name contains also the whole project name, so we need to remove it, so we can use it to a Gradle task
  return name.removePrefix("$projectName.")
}

/**
 * [filterArgument] can be one of [FILTER_ARG_BASELINE_PROFILE], [FILTER_ARG_MACROBENCHMARK]
 */
@VisibleForTesting
fun runConfigurationGradleTask(
  moduleName: String,
  flavorName: String?,
  filterArgument: String?,
) = buildString {
  append(":${moduleName}:")
  append(baselineProfileTaskName(flavorName))
  // Allows running only Baseline Profile generators (in case Macrobenchmarks are in the same module)
  if (filterArgument != null) {
    append(" -P${BaselineProfilesMacrobenchmarkCommon.FILTER_INSTR_ARG}=$filterArgument")
  }
}

/**
 * This parameter allows filtering only Baseline Profile generators as part of the run configuration (Gradle task).
 * If not added, the task would fail, because Macrobenchmarks by default can't run on an emulator (GMD).
 * Baseline profiles Gradle plugin adds this filtering parameter automatically from AGP 8.2.0, so we need to prevent adding it from then
 */
@VisibleForTesting
fun runConfigurationFilterArgument(agpVersion: AgpVersion): String? =
  if (agpVersion >= BP_PLUGIN_FILTERING_SUPPORTED) null else FILTER_ARG_BASELINE_PROFILE

@VisibleForTesting
fun baselineProfileTaskName(variantName: String?): String =
  "generate${variantName?.capitalize() ?: ""}BaselineProfile"