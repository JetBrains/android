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

import com.android.ide.common.repository.AgpVersion
import com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_STABLE_API
import com.android.tools.idea.gradle.model.impl.IdeProductFlavorImpl
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.npw.NewProjectWizardTestUtils.getAgpVersion
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.BaselineProfilesMacrobenchmarkCommon
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.BaselineProfilesMacrobenchmarkCommon.flavorsConfigurationsBuildGradle
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.BaselineProfilesMacrobenchmarkCommon.getTargetModelProductFlavors
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.ProductFlavorsWithDimensions
import com.android.tools.idea.npw.module.recipes.gitignore
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.wizard.template.ApiTemplateData
import com.android.tools.idea.wizard.template.ApiVersion
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.ProjectTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class BaselineProfilesMacrobenchmarkCommonTest {
  @get:Rule
  val projectRule = AndroidGradleProjectRule(agpVersionSoftwareEnvironment = getAgpVersion())

  @Test
  fun createModuleTest() {
    val mockExecutor = mock<RecipeExecutor>()
    val projectTemplateDataMock = mock<ProjectTemplateData>()
    val newModuleData = mock<ModuleTemplateData>()

    val macrobenchmarkMinRev = "1.2.0"
    val gradleContent = "gradlecontent"
    val moduleName = "module_name"
    val highestKnownApi = ApiVersion(HIGHEST_KNOWN_STABLE_API, HIGHEST_KNOWN_STABLE_API.toString())
    val apiTemplateData = ApiTemplateData(highestKnownApi, highestKnownApi, highestKnownApi, 0)
    val pluginVersion = AgpVersion.parse("0.0.0")

    var customizeCalled = false
    val customizeModule: RecipeExecutor.() -> Unit = { customizeCalled = true }

    whenever(projectTemplateDataMock.agpVersion).thenReturn(pluginVersion)
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
        customizeModule = customizeModule,
      )
    }

    verify(mockExecutor).run {
      addIncludeToSettings(moduleName)
      save(eq(gradleContent), any())
      applyPlugin("com.android.test", "com.android.tools.build:gradle", pluginVersion.toString())

      addDependency("androidx.test.ext:junit:+", "implementation")
      addDependency("androidx.test.espresso:espresso-core:+", "implementation")
      addDependency("androidx.test.uiautomator:uiautomator:+", "implementation")
      addDependency(
        "androidx.benchmark:benchmark-macro-junit4:+",
        "implementation",
        macrobenchmarkMinRev,
      )

      save(eq("<manifest />"), any())
      save(eq(gitignore()), any())
    }

    assertThat(customizeCalled).isTrue()
  }

  @Test
  fun flavorsConfigurationsBuildGradle_empty() {
    val flavorsBlock =
      flavorsConfigurationsBuildGradle(ProductFlavorsWithDimensions(emptyList(), emptyList()), true)
    assertThat(flavorsBlock).isEmpty()
  }

  @Test
  fun flavorsConfigurationsBuildGradle_only_dimensions() {
    val dimen = listOf("tier", "env")
    val flavorsBlock =
      flavorsConfigurationsBuildGradle(ProductFlavorsWithDimensions(dimen, emptyList()), true)

    assertThat(flavorsBlock).run {
      contains("flavorDimensions")
      doesNotContain("productFlavors")
    }
  }

  @Test
  fun flavorsConfigurationsBuildGradle_dimen_and_flavors() {
    val flavors =
      ProductFlavorsWithDimensions(
        listOf("tier", "env", "color"),
        listOf(
          ProductFlavorsWithDimensions.Item("demo", "env"),
          ProductFlavorsWithDimensions.Item("prod", "env"),
          ProductFlavorsWithDimensions.Item("free", "tier"),
          ProductFlavorsWithDimensions.Item("paid", "tier"),
          ProductFlavorsWithDimensions.Item("red", "color"),
          ProductFlavorsWithDimensions.Item("blue", "color"),
        ),
      )

    val flavorsBlock = flavorsConfigurationsBuildGradle(flavors, true)

    assertThat(flavorsBlock).run {
      contains("flavorDimensions")
      flavors.dimensions.forEach { contains(it) }
      contains("productFlavors")
      flavors.flavors.forEach {
        contains(it.name)
        contains("dimension = \"${it.dimension}\"")
      }
    }
  }

  @Test
  fun getTargetModelProductFlavors_fromIdeModel() {
    projectRule.load(TestProjectPaths.ANDROIDX_WITH_LIB_MODULE, agpVersion = getAgpVersion())

    val flavorMock1 = mock<IdeProductFlavorImpl>()
    whenever(flavorMock1.name).thenReturn("flavor1")
    whenever(flavorMock1.dimension).thenReturn("ide")

    val flavorMock2 = mock<IdeProductFlavorImpl>()
    whenever(flavorMock2.name).thenReturn("flavor2")
    whenever(flavorMock2.dimension).thenReturn("ide")

    val targetModuleGradleModel = mock<GradleAndroidModel>()
    whenever(targetModuleGradleModel.productFlavorNamesByFlavorDimension)
      .thenReturn(mapOf("ide" to listOf("flavor1", "flavor2")))

    val flavors = getTargetModelProductFlavors(targetModuleGradleModel)
    assertThat(flavors.dimensions).containsExactly("ide")
    assertThat(flavors.flavors)
      .containsExactly(
        ProductFlavorsWithDimensions.Item("flavor1", "ide"),
        ProductFlavorsWithDimensions.Item("flavor2", "ide"),
      )
  }

  @Test
  fun getTargetModelProductFlavors_fromIdeModel_twoDimensions() {
    projectRule.load(TestProjectPaths.ANDROIDX_WITH_LIB_MODULE, agpVersion = getAgpVersion())

    val flavorMock1 = mock<IdeProductFlavorImpl>()
    whenever(flavorMock1.name).thenReturn("flavor1")
    whenever(flavorMock1.dimension).thenReturn("ide")

    val flavorMock2 = mock<IdeProductFlavorImpl>()
    whenever(flavorMock2.name).thenReturn("flavor2")
    whenever(flavorMock2.dimension).thenReturn("env")

    val targetModuleGradleModel = mock<GradleAndroidModel>()
    whenever(targetModuleGradleModel.productFlavorNamesByFlavorDimension)
      .thenReturn(mapOf("ide" to listOf("flavor1"), "env" to listOf("flavor2")))

    val flavors = getTargetModelProductFlavors(targetModuleGradleModel)
    assertThat(flavors.dimensions).containsExactly("ide", "env")
    assertThat(flavors.flavors)
      .containsExactly(
        ProductFlavorsWithDimensions.Item("flavor1", "ide"),
        ProductFlavorsWithDimensions.Item("flavor2", "env"),
      )
  }
}
