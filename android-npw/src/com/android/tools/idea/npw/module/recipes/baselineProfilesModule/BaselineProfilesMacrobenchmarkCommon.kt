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

import com.android.SdkConstants
import com.android.tools.idea.npw.module.recipes.addKotlinIfNeeded
import com.android.tools.idea.npw.module.recipes.gitignore
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import org.jetbrains.kotlin.idea.gradleTooling.capitalize
import java.util.Locale

/**
 * Common class that serves functionality for Macrobenchmark and Baseline Profiles templates.
 */
object BaselineProfilesMacrobenchmarkCommon {
  const val FILTER_INSTR_ARG = "android.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules"
  const val FILTER_ARG_BASELINE_PROFILE = "BaselineProfile"
  const val FILTER_ARG_MACROBENCHMARK = "Macrobenchmark"

  /**
   * Creates a new Gradle module for Macrobenchmark / Baseline Profiles module.
   * @param buildGradleContent represents build.gradle(.kts) string
   * @param customizeModule is meant for creating benchmarks and optional setup
   */
  fun RecipeExecutor.createModule(
    newModule: ModuleTemplateData,
    useGradleKts: Boolean,
    macrobenchmarkMinRev: String,
    buildGradleContent: String,
    customizeModule: RecipeExecutor.() -> Unit = {},
  ) {
    addIncludeToSettings(newModule.name)

    // Create build.gradle(.kts) with the content from [buildGradle] lambda
    val buildFile = if (useGradleKts) SdkConstants.FN_BUILD_GRADLE_KTS else SdkConstants.FN_BUILD_GRADLE
    save(buildGradleContent, newModule.rootDir.resolve(buildFile))

    // Apply all required dependencies
    applyPlugin("com.android.test", newModule.projectTemplateData.gradlePluginVersion)

    addDependency("androidx.test.ext:junit:+", "implementation")
    addDependency("androidx.test.espresso:espresso-core:+", "implementation")
    addDependency("androidx.test.uiautomator:uiautomator:+", "implementation")
    addDependency("androidx.benchmark:benchmark-macro-junit4:+", "implementation", macrobenchmarkMinRev)

    // Add empty android manifest to be proper Android module
    save("<manifest />", newModule.rootDir.resolve("src/main/AndroidManifest.xml"))
    // Add gitignore with build/
    save(gitignore(), newModule.rootDir.resolve(".gitignore"))

    addKotlinIfNeeded(newModule.projectTemplateData, targetApi = newModule.apis.targetApi.api, noKtx = true)

    // Create (and open) the content test classes (benchmarks / baseline profile generator)
    customizeModule()
  }

  /**
   * Generates variants from product flavors.
   * @return If no product flavors, returns empty list
   */
  fun generateBuildVariants(
    dimensionNames: List<String>,
    productFlavorsAndDimensions: List<FlavorNameAndDimension>,
    buildType: String? = null,
  ): List<String> {
    val dimensionsWithFlavors = dimensionNames.map { dimensionName ->
      // flavor names grouped by its dimension
      productFlavorsAndDimensions
        .filter { (_, flavorDimension) -> flavorDimension == dimensionName }
        .map { it.name }
    }.toMutableList()

    // Add buildType (if defined) as the last one
    buildType?.let { dimensionsWithFlavors.add(listOf(it)) }

    // Check if at least one item exists
    if (dimensionsWithFlavors.isEmpty()) {
      return emptyList()
    }

    // We know that we have at least one flavor, so we use it as acc and combine with the rest of the list
    return dimensionsWithFlavors
      .subList(1, dimensionsWithFlavors.size)
      .fold(dimensionsWithFlavors[0]) { acc, flavorsByDimensions ->
        val newAcc = acc.flatMap { prefix ->
          flavorsByDimensions.map { flavorName -> prefix + flavorName.capitalize() }
        }

        newAcc
      }
      .toList()
  }

  fun flavorsConfigurationsBuildGradle(
    flavorDimensionNames: List<String>,
    flavorNamesAndDimensions: List<FlavorNameAndDimension>,
    useGradleKts: Boolean
  ): String {
    return buildString {
      if (flavorDimensionNames.isNotEmpty()) {
        val dimenString = flavorDimensionNames.joinToString(",") { "\"$it\"" }

        if (useGradleKts) {
          appendLine("flavorDimensions += listOf(${dimenString})")
        }
        else {
          appendLine("flavorDimensions += [$dimenString]")
        }
      }

      if (flavorNamesAndDimensions.isNotEmpty()) {
        appendLine("productFlavors {")
        flavorNamesAndDimensions.forEach {
          append(if (useGradleKts) "create(\"${it.name}\")" else it.name)
          appendLine("""{ dimension = "${it.dimension}" }""")
        }
        append("}")
      }
    }
  }
}

data class GmdSpec(val deviceName: String, val apiLevel: Int) {

  val identifier: String = buildString {
    // Pixel 6 -> pixel6
    append(deviceName.replace(" ", "").replaceFirstChar { it.lowercase(Locale.getDefault()) })
    append("Api$apiLevel")
  }

}

data class FlavorNameAndDimension(val name: String, val dimension: String)
