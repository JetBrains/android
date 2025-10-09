/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.tools.idea.gradle.model.impl.IdeLibraryModelResolverImpl
import com.android.tools.idea.gradle.model.impl.IdeVariantImpl
import com.android.tools.idea.gradle.project.model.GradleAndroidDependencyModel
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.model.GradleAndroidModelImpl
import com.intellij.openapi.module.Module
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.ExternalMappingKey
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntity

interface GradleAndroidModelEntity: WorkspaceEntity {
  @Parent
  val module: ModuleEntity
  val gradleAndroidModel: GradleAndroidModelImpl
  val resolvedVariant: IdeVariantImpl?

}

private val GRADLE_ANDROID_MODEL_KEY = ExternalMappingKey.create<GradleAndroidModel>("GRADLE_ANDROID_MODEL_KEY")

internal fun setGradleAndroidModelFromDataNode(
  storage: MutableEntityStorage,
  moduleEntity: ModuleEntity,
  coreModel: GradleAndroidModelImpl,
  resolver: IdeLibraryModelResolverImpl
) {
  storage.modifyModuleEntity(moduleEntity) {
    this.gradleAndroidModel = GradleAndroidModelEntity(
      entitySource = moduleEntity.entitySource,
      gradleAndroidModel = coreModel
    ) {
      this.resolvedVariant = coreModel.variants.find {
        it.name == coreModel.selectedVariantName
      }?.let { IdeVariantImpl(it, resolver) }
    }
  }
  updateGradleAndroidModelMapping(storage, moduleEntity)
}

internal fun attachDependenciesToModuleEntity(
  storage: MutableEntityStorage,
  moduleEntity: ModuleEntity,
  resolvedVariant: IdeVariantImpl
) {
  val gradleAndroidModel = moduleEntity.gradleAndroidModel ?: return
  storage.modifyModuleEntity(moduleEntity) {
    storage.modifyGradleAndroidModelEntity(gradleAndroidModel) {
      this.resolvedVariant = resolvedVariant
    }
  }
  updateGradleAndroidModelMapping(storage, moduleEntity)
}

internal fun updateGradleAndroidModelMapping(storage: MutableEntityStorage, moduleEntity: ModuleEntity) {
  val gradleAndroidModel = moduleEntity.gradleAndroidModel ?: return
  val mappedModel: GradleAndroidModel = gradleAndroidModel.resolvedVariant?.let {
    GradleAndroidDependencyModel.createWithSingleVariant(
      gradleAndroidModel.gradleAndroidModel,
      it
    )} ?: gradleAndroidModel.gradleAndroidModel
  storage.getMutableExternalMapping(GRADLE_ANDROID_MODEL_KEY)
    .addMapping(moduleEntity, mappedModel)
}

internal val ModuleEntity.gradleAndroidModel: GradleAndroidModelEntity?
  by WorkspaceEntity.extension()

fun EntityStorage.getGradleAndroidModel(module: Module): GradleAndroidModel? =
  module.findModuleEntity(this)?.let {
    getExternalMapping(GRADLE_ANDROID_MODEL_KEY).getDataByEntity(it)
  }
