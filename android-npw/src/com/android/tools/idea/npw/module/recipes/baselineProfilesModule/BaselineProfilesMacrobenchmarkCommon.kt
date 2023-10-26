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
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.npw.module.recipes.addKotlinIfNeeded
import com.android.tools.idea.npw.module.recipes.gitignore
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import java.util.Locale

/**
 * Common class that serves functionality for Macrobenchmark and Baseline Profiles templates.
 */
object BaselineProfilesMacrobenchmarkCommon {
  const val FILTER_INSTR_ARG = "android.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules"
  const val FILTER_ARG_BASELINE_PROFILE = "baselineprofile"
  const val FILTER_ARG_MACROBENCHMARK = "macrobenchmark"

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
    applyPlugin("com.android.test", newModule.projectTemplateData.agpVersion)

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

  fun flavorsConfigurationsBuildGradle(
    flavors: ProductFlavorsWithDimensions,
    useGradleKts: Boolean
  ): String {
    return buildString {
      if (flavors.dimensions.isNotEmpty()) {
        val dimenString = flavors.dimensions.joinToString(",") { "\"$it\"" }

        if (useGradleKts) {
          appendLine("flavorDimensions += listOf(${dimenString})")
        }
        else {
          appendLine("flavorDimensions += [$dimenString]")
        }
      }

      if (flavors.flavors.isNotEmpty()) {
        appendLine("productFlavors {")
        flavors.flavors.forEach { flavor ->
          append(if (useGradleKts) "create(\"${flavor.name}\")" else flavor.name)
          flavor.dimension?.let { appendLine("""{ dimension = "$it" }""") }
        }
        append("}")
      }
    }
  }

  /**
   * Retrieves the product flavors from [GradleAndroidModel].
   * We don't retrieve it from the DSL model, because it doesn't support loading flavors that aren't set in the build.gradle directly,
   * for example, when using convention plugins.
   */
  fun getTargetModelProductFlavors(targetModuleGradleModel: GradleAndroidModel): ProductFlavorsWithDimensions =
    ProductFlavorsWithDimensions(
      targetModuleGradleModel.productFlavorNamesByFlavorDimension.keys,
      targetModuleGradleModel.productFlavorNamesByFlavorDimension.flatMap { (dimension, flavors) ->
        flavors.map { flavorName -> ProductFlavorsWithDimensions.Item(flavorName, dimension) }
      }
    )
}

data class GmdSpec(val deviceName: String, val apiLevel: Int, val systemImageSource: String) {

  val identifier: String = buildString {
    // Pixel 6 -> pixel6
    append(deviceName.replace(" ", "").replaceFirstChar { it.lowercase(Locale.getDefault()) })
    append("Api$apiLevel")
  }

}

class ProductFlavorsWithDimensions(
  val dimensions: Collection<String>,
  val flavors: List<Item>
) {

  data class Item(val name: String, val dimension: String?)

  val flavorNamesGroupedByDimension = dimensions.map { dimensionName ->
    flavors
      .filter { it.dimension == dimensionName }
      .map { it.name }
  }

}
