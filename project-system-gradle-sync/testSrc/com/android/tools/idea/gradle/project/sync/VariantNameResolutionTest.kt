/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync

import com.android.tools.idea.gradle.model.impl.IdeProductFlavorImpl
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.buildAndroidProjectStub
import com.android.utils.appendCapitalized
import com.google.common.truth.Truth
import org.junit.Test
import java.io.File

private val PROJECT_ROOT = File("/")
private val APP_MODULE_ROOT = File("/app")

class VariantNameResolutionTest {

  @Test
  fun `matches when flavors in default order`() = matches(reorderFlavors = false)

  @Test
  fun `matches when flavors reordered`() = matches(reorderFlavors = true)

  private fun matches(reorderFlavors: Boolean) {
    val projectModelBuilder =
      AndroidProjectBuilder(
        flavorDimensions = { listOf("dim1", "dim2") },
        productFlavorsStub = { dimension ->
          when (dimension) {
            "dim1" -> listOf("firstAbc", "firstXyz")
            "dim2" -> listOf("secondAbc", "secondXyz")
            else -> error("Unknown: $dimension")
          }
            .flatMap { flavorName ->
              listOf(
                IdeProductFlavorImpl(
                  name = flavorName,
                  applicationIdSuffix = null,
                  versionNameSuffix = null,
                  resValues = emptyMap(),
                  proguardFiles = emptyList(),
                  consumerProguardFiles = emptyList(),
                  manifestPlaceholders = emptyMap(),
                  multiDexEnabled = null,
                  dimension = dimension,
                  applicationId = null,
                  versionCode = null,
                  versionName = flavorName + "Suffix",
                  minSdkVersion = null,
                  targetSdkVersion = null,
                  maxSdkVersion = null,
                  testApplicationId = "test".appendCapitalized(flavorName),
                  testInstrumentationRunner = null,
                  testInstrumentationRunnerArguments = emptyMap(),
                  testHandleProfiling = null,
                  testFunctionalTest = null,
                  resourceConfigurations = emptyList(),
                  vectorDrawables = null
                )
              )
            }
        },
        androidProject = {
          buildAndroidProjectStub()
            .let {
              if (reorderFlavors) {
                it.copy(
                  multiVariantData = it.multiVariantData?.copy(
                    productFlavors = it.multiVariantData?.productFlavors.orEmpty().reversed()
                  )
                )
              } else it
            }
        }
      )
        .build()
    val (androidProject, variants, ndkModel) =
      projectModelBuilder("projectName", ":app", PROJECT_ROOT, APP_MODULE_ROOT, "99.99.99-agp-version", InternedModels(null))

    val resolver = buildVariantNameResolver(androidProject, variants)
    Truth.assertThat(
      resolver.resolveVariant(
        "debug"
      ) {
        when (it) {
          "dim1" -> "firstAbc"
          "dim2" -> "secondXyz"
          else -> error("Unknown dimension: $it")
        }
      }).isEqualTo("firstAbcSecondXyzDebug")
  }
}
