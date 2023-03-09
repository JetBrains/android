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
package com.android.tools.idea.testing

import com.android.tools.idea.gradle.model.impl.IdeProductFlavorImpl
import com.android.tools.idea.gradle.project.sync.InternedModels
import com.android.utils.appendCapitalized
import com.google.common.truth.Expect
import org.junit.Rule
import org.junit.Test
import java.io.File

class AndroidProjectBuilderTest {
  @get:Rule
  val expect = Expect.createAndEnableStackTrace()

  @Test
  fun `multiple flavor support`() {
    val builder =
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
        }
      )
        .build()
    val model = builder(
      "projectName",
      ":app",
      File("/root"),
      File("/root/app"),
      "99.99-agp",
      InternedModels(null)
    )

    expect.that(model.androidProject.flavorDimensions).containsExactly("dim1", "dim2").inOrder()
    expect.that(model.variants.map { it.name })
      .containsAllOf("firstAbcSecondAbcDebug", "firstAbcSecondAbcRelease", "firstAbcSecondXyzDebug", "firstAbcSecondXyzRelease",
                     "firstXyzSecondAbcDebug", "firstXyzSecondAbcRelease", "firstXyzSecondXyzDebug", "firstXyzSecondXyzRelease")
    expect.that(model.variants.map { it.androidTestArtifact?.applicationId })
      .containsAllOf("testFirstAbc", "testFirstXyz", "testFirstXyz", "testFirstXyz", "testFirstXyz")
    expect.that(model.variants.map { it.deprecatedPreMergedTestApplicationId })
      .containsAllOf("testFirstAbc", "testFirstXyz", "testFirstXyz", "testFirstXyz", "testFirstXyz")
    expect.that(model.variants.map { it.versionNameWithSuffix })
      .containsAllOf("firstAbcSuffix", "firstAbcSuffix", "firstAbcSuffix", "firstAbcSuffix", "firstXyzSuffix", "firstXyzSuffix",
                     "firstXyzSuffix", "firstXyzSuffix")
    expect.that(model.variants.map { it.buildType }.distinct())
      .containsExactly("debug", "release")
  }
}
