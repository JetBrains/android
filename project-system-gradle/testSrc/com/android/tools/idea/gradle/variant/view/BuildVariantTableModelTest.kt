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
      appModuleBuilder(),
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
      appModuleBuilder(),
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
}

private fun appModuleBuilder(
  appPath: String = ":app",
  selectedVariant: String = "debug",
  dependOnVariant: String? = "debug"
) = AndroidModuleModelBuilder(
  appPath,
  selectedVariant,
  AndroidProjectBuilder(androidModuleDependencyList = { listOf(AndroidModuleDependency(":lib", dependOnVariant)) })
)

private fun libModuleBuilder(selectedVariant: String = "debug") =
  AndroidModuleModelBuilder(
    ":lib",
    selectedVariant,
    AndroidProjectBuilder(projectType = { IdeAndroidProjectType.PROJECT_TYPE_LIBRARY })
  )

private fun ndkLibModuleBuilder(selectedVariant: String = "debug") =
  AndroidModuleModelBuilder(
    ":lib",
    selectedVariant,
    AndroidProjectBuilder(projectType = { IdeAndroidProjectType.PROJECT_TYPE_LIBRARY }, ndkModel = { buildNdkModelStub() })
  )

