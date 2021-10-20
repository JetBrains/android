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

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.model.impl.IdeProductFlavorImpl
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.utils.appendCapitalized
import com.google.common.truth.Truth
import org.gradle.tooling.model.BuildIdentifier
import org.gradle.tooling.model.DomainObjectSet
import org.gradle.tooling.model.ProjectIdentifier
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.junit.Test
import java.io.File
import java.lang.UnsupportedOperationException

private val PROJECT_ROOT = File("/")
private val APP_MODULE_ROOT = File("/app")

class VariantNameResolutionTest {

  @Test
  fun `matches`() {
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
        }
      )
        .build()
    val (androidProject, variants, ndkModel) =
      projectModelBuilder("projectName", PROJECT_ROOT, APP_MODULE_ROOT, "99.99.99-agp-version")

    val module = AndroidModule(
      modelVersion = GradleVersion(99, 99),
      buildName = "rootBuild",
      buildNameMap = mapOf("rootBuild" to PROJECT_ROOT),
      gradleProject = APP_BASIC_GRADLE_PROJECT,
      androidProject = androidProject,
      allVariantNames = variants.map { it.name }.toSet(),
      defaultVariantName = variants.map { it.name }.first(),
      v2Variants = variants.toList(),
      nativeAndroidProject = null,
      nativeModule = ndkModel?.nativeModule
    )

    val resolver = module.buildVariantNameResolver()
    Truth.assertThat(
      resolver(
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

private val APP_BASIC_GRADLE_PROJECT = object : BasicGradleProject {
  override fun getProjectIdentifier(): ProjectIdentifier {
    return object : ProjectIdentifier {
      override fun getProjectPath(): String = ":app"
      override fun getBuildIdentifier(): BuildIdentifier = BuildIdentifier { PROJECT_ROOT }
    }
  }

  override fun getName(): String = throw UnsupportedOperationException()
  override fun getPath(): String = ":app"
  override fun getProjectDirectory(): File = throw UnsupportedOperationException()
  override fun getParent(): BasicGradleProject = throw UnsupportedOperationException()
  override fun getChildren(): DomainObjectSet<out BasicGradleProject> = throw UnsupportedOperationException()
}

