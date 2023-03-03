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
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.model.impl.IdeAndroidProjectImpl
import com.android.tools.idea.gradle.model.impl.IdeProductFlavorContainerImpl
import com.android.tools.idea.gradle.model.impl.IdeProductFlavorImpl
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.BaselineProfilesMacrobenchmarkCommon
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.BaselineProfilesMacrobenchmarkCommon.flavorsConfigurationsBuildGradle
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.BaselineProfilesMacrobenchmarkCommon.generateBuildVariants
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.BaselineProfilesMacrobenchmarkCommon.getTargetModelProductFlavors
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.ProductFlavorsWithDimensions
import com.android.tools.idea.npw.module.recipes.gitignore
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.findAppModule
import com.android.tools.idea.wizard.template.ApiTemplateData
import com.android.tools.idea.wizard.template.ApiVersion
import com.android.tools.idea.wizard.template.GradlePluginVersion
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.ProjectTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
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
  fun generateBuildVariants_emptyFlavors() {
    val flavors = ProductFlavorsWithDimensions(emptyList(), emptyList())
    val variants = generateBuildVariants(flavors)
    assertThat(variants).containsExactlyElementsIn(emptyList<String>())
  }

  @Test
  fun generateBuildVariants_emptyFlavors_with_buildType() {
    val flavors = ProductFlavorsWithDimensions(emptyList(), emptyList())
    val variants = generateBuildVariants(flavors, "release")
    assertThat(variants).containsExactlyElementsIn(listOf("release"))
  }

  @Test
  fun generateBuildVariants_oneDimensionOneFlavor() {
    val flavors = ProductFlavorsWithDimensions(
      listOf("env"),
      listOf(ProductFlavorsWithDimensions.Item("demo", "env"))
    )
    val variants = generateBuildVariants(flavors)
    assertThat(variants).containsExactlyElementsIn(listOf("demo"))
  }

  @Test
  fun generateBuildVariants_oneDimensionFlavors() {
    val flavors = ProductFlavorsWithDimensions(
      listOf("env"),
      listOf(
        ProductFlavorsWithDimensions.Item("demo", "env"),
        ProductFlavorsWithDimensions.Item("prod", "env"))
    )
    val variants = generateBuildVariants(flavors)
    assertThat(variants).containsExactlyElementsIn(listOf("demo", "prod"))
  }

  @Test
  fun generateBuildVariants_twoDimensionFlavors() {
    val flavors = ProductFlavorsWithDimensions(
      listOf("env", "tier"),
      listOf(
        ProductFlavorsWithDimensions.Item("demo", "env"),
        ProductFlavorsWithDimensions.Item("prod", "env"),
        ProductFlavorsWithDimensions.Item("free", "tier"),
        ProductFlavorsWithDimensions.Item("paid", "tier")
      )
    )
    val variants = generateBuildVariants(flavors)
    assertThat(variants).containsExactlyElementsIn(listOf("demoFree", "prodFree", "demoPaid", "prodPaid"))
  }

  @Test
  fun generateBuildVariants_twoDimensionOneTwoFlavors() {
    val flavors = ProductFlavorsWithDimensions(
      listOf("env", "tier"),
      listOf(
        ProductFlavorsWithDimensions.Item("demo", "env"),
        ProductFlavorsWithDimensions.Item("free", "tier"),
        ProductFlavorsWithDimensions.Item("paid", "tier")
      )
    )
    val variants = generateBuildVariants(flavors)
    assertThat(variants).containsExactlyElementsIn(listOf("demoFree", "demoPaid"))
  }

  @Test
  fun generateBuildVariants_twoDimensionFlavorsReversed() {
    val flavors = ProductFlavorsWithDimensions(
      listOf("tier", "env"),
      listOf(
        ProductFlavorsWithDimensions.Item("demo", "env"),
        ProductFlavorsWithDimensions.Item("prod", "env"),
        ProductFlavorsWithDimensions.Item("free", "tier"),
        ProductFlavorsWithDimensions.Item("paid", "tier")
      )
    )
    val variants = generateBuildVariants(flavors)
    assertThat(variants).containsExactlyElementsIn(listOf("freeDemo", "freeProd", "paidDemo", "paidProd"))
  }

  @Test
  fun generateBuildVariants_threeDimensionFlavorsReversed() {
    val flavors = ProductFlavorsWithDimensions(
      listOf("tier", "env", "color"),
      listOf(
        ProductFlavorsWithDimensions.Item("demo", "env"),
        ProductFlavorsWithDimensions.Item("prod", "env"),
        ProductFlavorsWithDimensions.Item("free", "tier"),
        ProductFlavorsWithDimensions.Item("paid", "tier"),
        ProductFlavorsWithDimensions.Item("red", "color"),
        ProductFlavorsWithDimensions.Item("blue", "color"),
      )
    )
    val variants = generateBuildVariants(flavors)
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
    val flavors = ProductFlavorsWithDimensions(
      listOf("tier", "env", "color"),
      listOf(
        ProductFlavorsWithDimensions.Item("demo", "env"),
        ProductFlavorsWithDimensions.Item("prod", "env"),
        ProductFlavorsWithDimensions.Item("free", "tier"),
        ProductFlavorsWithDimensions.Item("paid", "tier"),
        ProductFlavorsWithDimensions.Item("red", "color"),
        ProductFlavorsWithDimensions.Item("blue", "color"),
      )
    )
    val variants = generateBuildVariants(flavors, "release")
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
    val flavorsBlock = flavorsConfigurationsBuildGradle(ProductFlavorsWithDimensions(emptyList(), emptyList()), true)
    assertThat(flavorsBlock).isEmpty()
  }

  @Test
  fun flavorsConfigurationsBuildGradle_only_dimensions() {
    val dimen = listOf("tier", "env")
    val flavorsBlock = flavorsConfigurationsBuildGradle(ProductFlavorsWithDimensions(dimen, emptyList()), true)

    assertThat(flavorsBlock).run {
      contains("flavorDimensions")
      doesNotContain("productFlavors")
    }
  }

  @Test
  fun flavorsConfigurationsBuildGradle_dimen_and_flavors() {
    val flavors = ProductFlavorsWithDimensions(
      listOf("tier", "env", "color"),
      listOf(
        ProductFlavorsWithDimensions.Item("demo", "env"),
        ProductFlavorsWithDimensions.Item("prod", "env"),
        ProductFlavorsWithDimensions.Item("free", "tier"),
        ProductFlavorsWithDimensions.Item("paid", "tier"),
        ProductFlavorsWithDimensions.Item("red", "color"),
        ProductFlavorsWithDimensions.Item("blue", "color"),
      )
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
    projectRule.load(TestProjectPaths.ANDROIDX_WITH_LIB_MODULE)

    val targetModule = projectRule.project.findAppModule()
    val projectBuildModel = ProjectBuildModel.getOrLog(targetModule.project)!!
    val targetModuleAndroidModel = projectBuildModel.getModuleBuildModel(targetModule)!!.android()

    val flavorMock1 = MockitoKt.mock<IdeProductFlavorImpl>()
    whenever(flavorMock1.name).thenReturn("flavor1")
    whenever(flavorMock1.dimension).thenReturn("ide")

    val flavorMock2 = MockitoKt.mock<IdeProductFlavorImpl>()
    whenever(flavorMock2.name).thenReturn("flavor2")
    whenever(flavorMock2.dimension).thenReturn("ide")

    val ideProject = (GradleAndroidModel.get(targetModule)!!.androidProject as IdeAndroidProjectImpl)
    val targetModuleIdeProject = ideProject.copy(
      flavorDimensions = listOf("ide"),
      multiVariantData = ideProject.multiVariantData?.copy(
        productFlavors = listOf(
          IdeProductFlavorContainerImpl(flavorMock1, null, emptyList()),
          IdeProductFlavorContainerImpl(flavorMock2, null, emptyList())
        ),
      )
    )

    val flavors = getTargetModelProductFlavors(targetModuleIdeProject, targetModuleAndroidModel)
    assertThat(flavors.dimensions).containsExactly("ide")
    assertThat(flavors.flavors).containsExactly(
      ProductFlavorsWithDimensions.Item("flavor1", "ide"),
      ProductFlavorsWithDimensions.Item("flavor2", "ide")
    )

  }

  @Test
  fun getTargetModelProductFlavors_fromDsl() {
    projectRule.load(TestProjectPaths.ANDROIDX_WITH_LIB_MODULE)

    val targetModule = projectRule.project.findAppModule()
    val projectBuildModel = ProjectBuildModel.getOrLog(targetModule.project)!!
    val targetModuleIdeProject = GradleAndroidModel.get(targetModule)?.androidProject

    val targetModuleAndroidModel = projectBuildModel.getModuleBuildModel(targetModule)!!.android()
    targetModuleAndroidModel.flavorDimensions().addListValue()!!.setValue("env")

    targetModuleAndroidModel.addProductFlavor("demo").also {
      it.dimension().setValue("env")
    }
    targetModuleAndroidModel.addProductFlavor("prod").also {
      it.dimension().setValue("env")
    }

    ApplicationManager.getApplication().invokeAndWait {
      WriteCommandAction.runWriteCommandAction(projectRule.project) { projectBuildModel.applyChanges() }
    }

    val flavors = getTargetModelProductFlavors(targetModuleIdeProject, targetModuleAndroidModel)

    assertThat(flavors.dimensions).containsExactly("env")
    assertThat(flavors.flavors).containsExactly(
      ProductFlavorsWithDimensions.Item("demo", "env"),
      ProductFlavorsWithDimensions.Item("prod", "env")
    )
  }

}
