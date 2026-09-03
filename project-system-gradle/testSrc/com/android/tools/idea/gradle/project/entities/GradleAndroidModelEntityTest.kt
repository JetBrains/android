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
package com.android.tools.idea.gradle.project.entities

import com.android.tools.idea.gradle.model.impl.IdeVariantImpl
import com.android.tools.idea.gradle.project.model.GradleAndroidModelImpl
import com.google.common.truth.Truth.assertThat
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@RunWith(JUnit4::class)
class GradleAndroidModelEntityTest {

  // JetBrains: an anonymous object, because AllIntellijEntitiesGenerationTest reads a named `object : EntitySource` as workspace code and asks this test module for a generated source root
  private val TestEntitySource = object : EntitySource {}

  private fun createMockModel(moduleName: String): GradleAndroidModelImpl {
    val model = mock(GradleAndroidModelImpl::class.java)
    `when`(model.moduleName).thenReturn(moduleName)
    return model
  }

  @Test
  fun testCreateAndResolveGradleAndroidModelEntity() {
    val storage = MutableEntityStorage.create()
    val mockModel = createMockModel("app")

    val moduleEntity =
      storage.addEntity(
        ModuleEntity(
          name = "app",
          dependencies = emptyList(),
          entitySource = TestEntitySource,
        )
      )

    storage.modifyModuleEntity(moduleEntity) {
      this.gradleAndroidModel =
        GradleAndroidModelEntity(
          gradleAndroidModel = mockModel,
          entitySource = TestEntitySource,
        )
    }

    val resolvedEntity = storage.resolve(GradleAndroidModelEntityId(ModuleId("app")))
    assertThat(resolvedEntity).isNotNull()
    assertThat(resolvedEntity!!.gradleAndroidModel).isEqualTo(mockModel)
    assertThat(resolvedEntity.module.name).isEqualTo("app")
    assertThat(resolvedEntity.symbolicId.moduleId.name).isEqualTo("app")
    assertThat(resolvedEntity.symbolicId.presentableName).isEqualTo("GradleAndroidModelEntity for app")
  }

  @Test
  fun testBuilderModuleGetterAndSetterWithDiff() {
    val storage = MutableEntityStorage.create()
    val mockModel = createMockModel("app")

    val moduleEntity =
      storage.addEntity(
        ModuleEntity(
          name = "app",
          dependencies = emptyList(),
          entitySource = TestEntitySource,
        )
      )

    storage.modifyModuleEntity(moduleEntity) {
      this.gradleAndroidModel =
        GradleAndroidModelEntity(
          gradleAndroidModel = mockModel,
          entitySource = TestEntitySource,
        )
    }

    val entity = storage.resolve(GradleAndroidModelEntityId(ModuleId("app")))!!
    var retrievedModuleName: String? = null

    storage.modifyGradleAndroidModelEntity(entity) {
      // Exercises Builder.module getter when diff != null
      retrievedModuleName = this.module.name
    }

    assertThat(retrievedModuleName).isEqualTo("app")
  }

  @Test
  fun testBuilderModuleGetterAndSetterWithoutDiff() {
    val mockModel = createMockModel("app")
    val moduleBuilder =
      ModuleEntity(
        name = "app",
        dependencies = emptyList(),
        entitySource = TestEntitySource,
      )

    val entityBuilder =
      GradleAndroidModelEntity(
        gradleAndroidModel = mockModel,
        entitySource = TestEntitySource,
      ) {
        // Exercises Builder.module setter when diff == null
        this.module = moduleBuilder
      }

    // Exercises Builder.module getter when diff == null
    assertThat(entityBuilder.module).isEqualTo(moduleBuilder)
    assertThat(entityBuilder.module.name).isEqualTo("app")
  }

  @Test
  fun testModifyGradleAndroidModelEntityProperties() {
    val storage = MutableEntityStorage.create()
    val initialModel = createMockModel("app")
    val updatedModel = createMockModel("app")
    val mockVariant = mock(IdeVariantImpl::class.java)

    val moduleEntity =
      storage.addEntity(
        ModuleEntity(
          name = "app",
          dependencies = emptyList(),
          entitySource = TestEntitySource,
        )
      )

    storage.modifyModuleEntity(moduleEntity) {
      this.gradleAndroidModel =
        GradleAndroidModelEntity(
          gradleAndroidModel = initialModel,
          entitySource = TestEntitySource,
        )
    }

    val entity = storage.resolve(GradleAndroidModelEntityId(ModuleId("app")))!!

    storage.modifyGradleAndroidModelEntity(entity) {
      this.gradleAndroidModel = updatedModel
      this.resolvedVariant = mockVariant
    }

    val updatedEntity = storage.resolve(GradleAndroidModelEntityId(ModuleId("app")))!!
    assertThat(updatedEntity.gradleAndroidModel).isEqualTo(updatedModel)
    assertThat(updatedEntity.resolvedVariant).isEqualTo(mockVariant)
  }

  @Test
  fun testGradleAndroidModelEntityId() {
    val id1 = GradleAndroidModelEntityId(ModuleId("app"))
    val id2 = GradleAndroidModelEntityId(ModuleId("app"))
    val id3 = GradleAndroidModelEntityId(ModuleId("lib"))

    assertThat(id1).isEqualTo(id2)
    assertThat(id1.hashCode()).isEqualTo(id2.hashCode())
    assertThat(id1).isNotEqualTo(id3)
    assertThat(id1.presentableName).isEqualTo("GradleAndroidModelEntity for app")
  }
}
