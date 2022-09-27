/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.variant.view

import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.testing.AndroidModuleDependency
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.EdtAndroidProjectRule
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.android.tools.idea.testing.buildNdkModelStub
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Expect
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@RunsInEdt
class BuildVariantTableModelTest {
  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @get:Rule
  val projectRule: EdtAndroidProjectRule = AndroidProjectRule.withAndroidModels().onEdt()

  @Test
  fun withoutAbi() {
    projectRule.setupProjectFrom(
      JavaModuleModelBuilder.rootModuleBuilder,
      appModuleBuilder(dependencyList = listOf(AndroidModuleDependency(":lib", "debug"))),
      libModuleBuilder()
    )
    val model = BuildVariantTableModel.create(projectRule.project)
    val appModule = projectRule.project.gradleModule(":app")!!
    val libModule = projectRule.project.gradleModule(":lib")!!

    expect.that(model.rows).hasSize(2)
    expect.that(model.rows.getOrNull(0))
      .isEqualTo(
        BuildVariantTableRow(
          module = appModule,
          variant = "debug",
          abi = null,
          buildVariants = listOf(BuildVariantItem("debug"), BuildVariantItem("release")),
          abis = emptyList()
        )
      )
    expect.that(model.rows.getOrNull(1))
      .isEqualTo(
        BuildVariantTableRow(
          module = libModule,
          variant = "debug",
          abi = null,
          buildVariants = listOf(BuildVariantItem("debug"), BuildVariantItem("release")),
          abis = emptyList()
        )
      )
  }

  @Test
  fun withAbi() {
    projectRule.setupProjectFrom(
      JavaModuleModelBuilder.rootModuleBuilder,
      appModuleBuilder(dependencyList = listOf(AndroidModuleDependency(":lib", "debug"))),
      ndkLibModuleBuilder()
    )
    val model = BuildVariantTableModel.create(projectRule.project)
    val appModule = projectRule.project.gradleModule(":app")!!
    val libModule = projectRule.project.gradleModule(":lib")!!

    expect.that(model.rows).hasSize(2)
    expect.that(model.rows.getOrNull(0))
      .isEqualTo(
        BuildVariantTableRow(
          module = appModule,
          variant = "debug",
          abi = null,
          buildVariants = listOf(BuildVariantItem("debug"), BuildVariantItem("release")),
          abis = emptyList()
        )
      )
    expect.that(model.rows.getOrNull(1))
      .isEqualTo(
        BuildVariantTableRow(
          module = libModule,
          variant = "debug",
          abi = "x86_64",
          buildVariants = listOf(BuildVariantItem("debug"), BuildVariantItem("release")),
          abis = listOf(AbiItem("arm64-v8a"), AbiItem("x86_64"))
        )
      )
  }

  @Test
  fun `Given project modules with same type When create VariantTableModel Then TableRows is sorted`() {
    val projectModules = arrayOf(
      JavaModuleModelBuilder.rootModuleBuilder,
      appModuleBuilder(":appB"),
      appModuleBuilder(":appA"),
      appModuleBuilder(":appD"),
      appModuleBuilder(":appC")
    )
    projectRule.setupProjectFrom(*projectModules)

    val model = BuildVariantTableModel.create(projectRule.project)

    expect.that(model.rows).hasSize(4)
    // Assert modules with same type are sorted
    val expectedSortedModules = arrayOf("appA", "appB", "appC", "appD")
    expectedSortedModules.forEachIndexed { index, moduleName ->
      expect.that(model.rows[index].module.name).isEqualTo("${projectRule.project.name}.$moduleName")
    }
  }

  @Test
  fun `Given project modules with different type When create VariantTableModel Then TableRows is sorted by IdeAndroidProjectType`() {
    val projectModules = arrayOf(
      JavaModuleModelBuilder.rootModuleBuilder,
      appModuleBuilder(":xappC"),
      appModuleBuilder(":appB"),
      appModuleBuilder(":appA"),
      featureModuleBuilder(),
      libModuleBuilder(":libB"),
      libModuleBuilder(":alibA"),
      testModuleBuilder()
    )
    projectRule.setupProjectFrom(*projectModules)

    val model = BuildVariantTableModel.create(projectRule.project)

    expect.that(model.rows).hasSize(7)
    // Assert modules sorted and grouped by IdeAndroidProjectType order
    val expectedSortedModules = arrayOf("appA", "appB", "xappC", "alibA", "libB", "test", "feature")
    expectedSortedModules.forEachIndexed { index, moduleName ->
      expect.that(model.rows[index].module.name).isEqualTo("${projectRule.project.name}.$moduleName")
    }
  }
}

private fun appModuleBuilder(
  appPath: String = ":app",
  selectedVariant: String = "debug",
  dependencyList: List<AndroidModuleDependency> = emptyList()
) =
  AndroidModuleModelBuilder(
    appPath,
    selectedVariant,
    AndroidProjectBuilder(androidModuleDependencyList = { dependencyList })
  )

private fun libModuleBuilder(
  libPath: String = ":lib",
  selectedVariant: String = "debug"
) =
  AndroidModuleModelBuilder(
    libPath,
    selectedVariant,
    AndroidProjectBuilder(projectType = { IdeAndroidProjectType.PROJECT_TYPE_LIBRARY })
  )

private fun ndkLibModuleBuilder(selectedVariant: String = "debug") =
  AndroidModuleModelBuilder(
    ":lib",
    selectedVariant,
    AndroidProjectBuilder(projectType = { IdeAndroidProjectType.PROJECT_TYPE_LIBRARY }, ndkModel = { buildNdkModelStub() })
  )

private fun featureModuleBuilder(
  featurePath: String = ":feature",
  selectedVariant: String = "debug"
) =
  AndroidModuleModelBuilder(
    featurePath,
    selectedVariant,
    AndroidProjectBuilder(projectType = { IdeAndroidProjectType.PROJECT_TYPE_FEATURE })
  )

private fun testModuleBuilder(
  testPath: String = ":test",
  selectedVariant: String = "debug"
) =
  AndroidModuleModelBuilder(
    testPath,
    selectedVariant,
    AndroidProjectBuilder(projectType = { IdeAndroidProjectType.PROJECT_TYPE_TEST })
  )
