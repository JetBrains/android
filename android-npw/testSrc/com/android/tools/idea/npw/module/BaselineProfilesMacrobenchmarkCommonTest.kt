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
package com.android.tools.idea.npw.module

import com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_STABLE_API
import com.android.testutils.MockitoKt
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.BaselineProfilesMacrobenchmarkCommon
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.BaselineProfilesMacrobenchmarkCommon.FILTER_ARG_BASELINE_PROFILE
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.BaselineProfilesMacrobenchmarkCommon.flavorsConfigurationsBuildGradle
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.BaselineProfilesMacrobenchmarkCommon.generateBuildVariants
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.FlavorNameAndDimension
import com.android.tools.idea.npw.module.recipes.gitignore
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.wizard.template.ApiTemplateData
import com.android.tools.idea.wizard.template.ApiVersion
import com.android.tools.idea.wizard.template.GradlePluginVersion
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.ProjectTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.verify
import java.io.File

class BaselineProfilesMacrobenchmarkCommonTest {
  @get:Rule
  val projectRule = AndroidGradleProjectRule()

  @Test
  fun createModuleTest() {
    val mockExecutor = MockitoKt.mock<RecipeExecutor>()
    val projectTemplateDataMock = MockitoKt.mock<ProjectTemplateData>()
    val newModuleData = MockitoKt.mock<ModuleTemplateData>()

    val macrobenchmarkMinRev = "1.2.0"
    val gradleContent = "gradlecontent"
    val moduleName = "module_name"
    val highestKnownApi = ApiVersion(HIGHEST_KNOWN_STABLE_API, HIGHEST_KNOWN_STABLE_API.toString())
    val apiTemplateData = ApiTemplateData(highestKnownApi, highestKnownApi, highestKnownApi, 0)
    val pluginVersion = GradlePluginVersion()

    var customizeCalled = false
    val customizeModule: RecipeExecutor.() -> Unit = {
      customizeCalled = true
    }

    whenever(projectTemplateDataMock.gradlePluginVersion).thenReturn(pluginVersion)
    whenever(projectTemplateDataMock.language).thenReturn(Language.Kotlin)
    whenever(projectTemplateDataMock.kotlinVersion).thenReturn("1.8.10")
    whenever(projectTemplateDataMock.androidXSupport).thenReturn(true)

    whenever(newModuleData.projectTemplateData).thenReturn(projectTemplateDataMock)
    whenever(newModuleData.rootDir).thenReturn(File(""))
    whenever(newModuleData.apis).thenReturn(apiTemplateData)
    whenever(newModuleData.name).thenReturn(moduleName)

    with(BaselineProfilesMacrobenchmarkCommon) {
      mockExecutor.createModule(
        newModule = newModuleData,
        useGradleKts = true,
        macrobenchmarkMinRev = macrobenchmarkMinRev,
        buildGradleContent = gradleContent,
        customizeModule = customizeModule
      )
    }

    verify(mockExecutor).run {
      addIncludeToSettings(moduleName)
      save(eq(gradleContent), MockitoKt.any())
      applyPlugin("com.android.test", pluginVersion)

      addDependency("androidx.test.ext:junit:+", "implementation")
      addDependency("androidx.test.espresso:espresso-core:+", "implementation")
      addDependency("androidx.test.uiautomator:uiautomator:+", "implementation")
      addDependency("androidx.benchmark:benchmark-macro-junit4:+", "implementation", macrobenchmarkMinRev)

      save(eq("<manifest />"), MockitoKt.any())
      save(eq(gitignore()), MockitoKt.any())
    }

    assertThat(customizeCalled).isTrue()
  }

  @Test
  fun runConfiguration() {
    val task = BaselineProfilesMacrobenchmarkCommon.runConfigurationGradleTask("moduleName", "variantName", FILTER_ARG_BASELINE_PROFILE)
    assertThat(task).run {
      contains(":moduleName:generateVariantNameBaselineProfiles")
      contains("-P${BaselineProfilesMacrobenchmarkCommon.FILTER_INSTR_ARG}=BaselineProfile")
    }
  }

  @Test
  fun runConfigurationNoFilter() {
    val task = BaselineProfilesMacrobenchmarkCommon.runConfigurationGradleTask("moduleName", "variantName", null)
    assertThat(task).run {
      contains(":moduleName:generateVariantNameBaselineProfiles")
      doesNotContain("-P${BaselineProfilesMacrobenchmarkCommon.FILTER_INSTR_ARG}")
    }
  }

  @Test
  fun generateBuildVariants_emptyFlavors() {
    val variants = generateBuildVariants(emptyList(), emptyList())
    assertThat(variants).containsExactlyElementsIn(listOf(null))
  }

  @Test
  fun generateBuildVariants_emptyFlavors_with_buildType() {
    val variants = generateBuildVariants(emptyList(), emptyList(), "release")
    assertThat(variants).containsExactlyElementsIn(listOf("release"))
  }

  @Test
  fun generateBuildVariants_oneDimensionOneFlavor() {
    val dimen = listOf("env")
    val flavors = listOf(FlavorNameAndDimension("demo", "env"))
    val variants = generateBuildVariants(dimen, flavors)

    assertThat(variants).containsExactlyElementsIn(listOf("demo"))
  }

  @Test
  fun generateBuildVariants_oneDimensionFlavors() {
    val dimen = listOf("env")
    val flavors = listOf(FlavorNameAndDimension("demo", "env"), FlavorNameAndDimension("prod", "env"))

    val variants = generateBuildVariants(dimen, flavors)
    assertThat(variants).containsExactlyElementsIn(listOf("demo", "prod"))
  }

  @Test
  fun generateBuildVariants_twoDimensionFlavors() {
    val dimen = listOf("env", "tier")
    val flavors = listOf(
      FlavorNameAndDimension("demo", "env"),
      FlavorNameAndDimension("prod", "env"),
      FlavorNameAndDimension("free", "tier"),
      FlavorNameAndDimension("paid", "tier")
    )

    val variants = generateBuildVariants(dimen, flavors)
    assertThat(variants).containsExactlyElementsIn(listOf("demoFree", "prodFree", "demoPaid", "prodPaid"))
  }

  @Test
  fun generateBuildVariants_twoDimensionOneTwoFlavors() {
    val dimen = listOf("env", "tier")
    val flavors = listOf(
      FlavorNameAndDimension("demo", "env"),
      FlavorNameAndDimension("free", "tier"),
      FlavorNameAndDimension("paid", "tier")
    )

    val variants = generateBuildVariants(dimen, flavors)
    assertThat(variants).containsExactlyElementsIn(listOf("demoFree", "demoPaid"))
  }

  @Test
  fun generateBuildVariants_twoDimensionFlavorsReversed() {
    val dimen = listOf("tier", "env")
    val flavors = listOf(
      FlavorNameAndDimension("demo", "env"),
      FlavorNameAndDimension("prod", "env"),
      FlavorNameAndDimension("free", "tier"),
      FlavorNameAndDimension("paid", "tier")
    )

    val variants = generateBuildVariants(dimen, flavors)
    assertThat(variants).containsExactlyElementsIn(listOf("freeDemo", "freeProd", "paidDemo", "paidProd"))
  }

  @Test
  fun generateBuildVariants_threeDimensionFlavorsReversed() {
    val dimen = listOf("tier", "env", "color")
    val flavors = listOf(
      FlavorNameAndDimension("demo", "env"),
      FlavorNameAndDimension("prod", "env"),
      FlavorNameAndDimension("free", "tier"),
      FlavorNameAndDimension("paid", "tier"),
      FlavorNameAndDimension("red", "color"),
      FlavorNameAndDimension("blue", "color"),
    )
    val variants = generateBuildVariants(dimen, flavors)

    assertThat(variants).containsExactlyElementsIn(listOf(
      "freeDemoRed",
      "freeDemoBlue",
      "freeProdRed",
      "freeProdBlue",
      "paidDemoRed",
      "paidDemoBlue",
      "paidProdRed",
      "paidProdBlue",
    ))
  }

  @Test
  fun generateBuildVariants_threeDimensionFlavorsReversedWithBuildType() {
    val dimen = listOf("tier", "env", "color")
    val flavors = listOf(
      FlavorNameAndDimension("demo", "env"),
      FlavorNameAndDimension("prod", "env"),
      FlavorNameAndDimension("free", "tier"),
      FlavorNameAndDimension("paid", "tier"),
      FlavorNameAndDimension("red", "color"),
      FlavorNameAndDimension("blue", "color"),
    )
    val variants = generateBuildVariants(dimen, flavors, "release")

    assertThat(variants).containsExactlyElementsIn(listOf(
      "freeDemoRedRelease",
      "freeDemoBlueRelease",
      "freeProdRedRelease",
      "freeProdBlueRelease",
      "paidDemoRedRelease",
      "paidDemoBlueRelease",
      "paidProdRedRelease",
      "paidProdBlueRelease",
    ))
  }

  @Test
  fun flavorsConfigurationsBuildGradle_empty() {
    val flavorsBlock = flavorsConfigurationsBuildGradle(emptyList(), emptyList(), true)
    assertThat(flavorsBlock).isEmpty()
  }

  @Test
  fun flavorsConfigurationsBuildGradle_only_dimensions() {
    val dimen = listOf("tier", "env")
    val flavorsBlock = flavorsConfigurationsBuildGradle(dimen, emptyList(), true)

    assertThat(flavorsBlock).run {
      contains("flavorDimensions")
      doesNotContain("productFlavors")
    }
  }

  @Test
  fun flavorsConfigurationsBuildGradle_dimen_and_flavors() {
    val dimen = listOf("tier", "env", "color")
    val flavors = listOf(
      FlavorNameAndDimension("demo", "env"),
      FlavorNameAndDimension("prod", "env"),
      FlavorNameAndDimension("free", "tier"),
      FlavorNameAndDimension("paid", "tier"),
      FlavorNameAndDimension("red", "color"),
      FlavorNameAndDimension("blue", "color"),
    )

    val flavorsBlock = flavorsConfigurationsBuildGradle(dimen, flavors, true)

    assertThat(flavorsBlock).run {
      contains("flavorDimensions")
      dimen.forEach { contains(it) }
      contains("productFlavors")
      flavors.forEach {
        contains(it.name)
        contains("dimension = \"${it.dimension}\"")
      }
    }
  }

}
