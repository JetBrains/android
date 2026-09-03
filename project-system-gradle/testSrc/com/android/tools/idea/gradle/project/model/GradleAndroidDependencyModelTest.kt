/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.model

import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.EdtAndroidProjectRule
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@RunsInEdt
class GradleAndroidDependencyModelTest {

  @get:Rule val projectRule: EdtAndroidProjectRule = AndroidProjectRule.withAndroidModels().onEdt()

  @Test
  fun testHashCodeAndEquals() {
    projectRule.setupProjectFrom(
      JavaModuleModelBuilder.rootModuleBuilder,
      AndroidModuleModelBuilder(":app", "debug", AndroidProjectBuilder()),
      AndroidModuleModelBuilder(":lib", "debug", AndroidProjectBuilder(projectType = { IdeAndroidProjectType.PROJECT_TYPE_LIBRARY })),
    )

    val appModule = projectRule.project.gradleModule(":app")!!
    val libModule = projectRule.project.gradleModule(":lib")!!

    val appModel = GradleAndroidDependencyModel.get(appModule)!!
    val libModel = GradleAndroidDependencyModel.get(libModule)!!

    // Test hashCode implementation and consistency with coreModel
    val coreModel = (appModel as GradleAndroidDependencyModelImpl).coreModel
    assertThat(appModel.hashCode()).isEqualTo(coreModel.hashCode())
    assertThat(appModel.hashCode()).isNotEqualTo(libModel.hashCode())

    // Test hash set / map behavior with hashCode
    val modelSet = hashSetOf(appModel)
    assertThat(modelSet).contains(appModel)
    assertThat(modelSet).doesNotContain(libModel)

    // Test equals
    assertThat(appModel).isEqualTo(appModel)
    assertThat(appModel).isNotEqualTo(libModel)
    assertThat(appModel).isNotEqualTo(null)
    assertThat(appModel).isNotEqualTo("other")
  }

  @Test
  fun testGetFromModuleAndFacet() {
    projectRule.setupProjectFrom(
      JavaModuleModelBuilder.rootModuleBuilder,
      AndroidModuleModelBuilder(":app", "debug", AndroidProjectBuilder()),
    )

    val appModule = projectRule.project.gradleModule(":app")!!
    val modelFromModule = GradleAndroidDependencyModel.get(appModule)
    assertThat(modelFromModule).isNotNull()

    val facet = AndroidFacet.getInstance(appModule)!!
    val modelFromFacet = GradleAndroidDependencyModel.get(facet)
    assertThat(modelFromFacet).isNotNull()
    assertThat(modelFromFacet).isEqualTo(modelFromModule)
  }

  @Test
  fun testArtifactsAndDependenciesAccessors() {
    projectRule.setupProjectFrom(
      JavaModuleModelBuilder.rootModuleBuilder,
      AndroidModuleModelBuilder(":app", "debug", AndroidProjectBuilder()),
    )

    val appModule = projectRule.project.gradleModule(":app")!!
    val model = GradleAndroidDependencyModel.get(appModule)!!

    assertThat(model.selectedVariantWithDependencies).isNotNull()
    assertThat(model.selectedVariantWithDependencies.name).isEqualTo("debug")
    assertThat(model.variantsWithDependencies).isNotEmpty()
    assertThat(model.mainArtifactWithDependencies).isNotNull()
    assertThat(model.getArtifactForAndroidTest()).isNotNull()
    assertThat(model.selectedAndroidTestCompileDependencies).isNotNull()
  }

  @Test
  fun testCreateWithSingleVariant() {
    projectRule.setupProjectFrom(
      JavaModuleModelBuilder.rootModuleBuilder,
      AndroidModuleModelBuilder(":app", "debug", AndroidProjectBuilder()),
    )

    val appModule = projectRule.project.gradleModule(":app")!!
    val existingModel = GradleAndroidDependencyModel.get(appModule) as GradleAndroidDependencyModelImpl
    val coreModel = existingModel.coreModel as GradleAndroidModelImpl
    val variant = existingModel.selectedVariantWithDependencies

    val singleVariantModel = GradleAndroidDependencyModel.createWithSingleVariant(coreModel, variant)
    assertThat(singleVariantModel.hashCode()).isEqualTo(coreModel.hashCode())
    assertThat(singleVariantModel).isEqualTo(existingModel)
    assertThat(singleVariantModel.selectedVariantWithDependencies).isEqualTo(variant)
    assertThat(singleVariantModel.variantsWithDependencies).containsExactly(variant)
  }
}
