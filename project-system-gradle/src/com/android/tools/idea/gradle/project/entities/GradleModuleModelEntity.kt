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

import com.android.tools.idea.gradle.project.model.GradleModuleModel
import com.intellij.openapi.module.Module
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.annotations.Parent

data class GradleModuleModelEntityId(val moduleId: ModuleId) : SymbolicEntityId<GradleModuleModelEntity> {
  override val presentableName: String
    get() = "GradleModuleModelEntity for ${moduleId.presentableName}"
}

interface GradleModuleModelEntity: WorkspaceEntityWithSymbolicId {
  override val symbolicId: GradleModuleModelEntityId
    get() = GradleModuleModelEntityId(ModuleId(gradleModuleModel.moduleName))

  @Parent
  val module: ModuleEntity
  val gradleModuleModel: GradleModuleModel
}

internal fun setGradleModuleModelFromDataNode(storage: MutableEntityStorage, module: Module, model: GradleModuleModel) {
  val moduleEntity = checkNotNull(storage.resolve(ModuleId(module.name))) { "Can't find module entity for ${module.name}"}
  // We still need to create a new entity when the module itself is not supported by sync contributors
  modifyExistingEntity(storage, moduleEntity, model) ?: createNewEntity(storage, moduleEntity, model)
}

private fun createNewEntity(storage: MutableEntityStorage, moduleEntity: ModuleEntity, model: GradleModuleModel): ModuleEntity =
  storage.modifyModuleEntity(moduleEntity) {
    this.gradleModuleModel = GradleModuleModelEntity(
      entitySource = this@modifyModuleEntity.entitySource,
      gradleModuleModel = model
    )
  }

private fun modifyExistingEntity(storage: MutableEntityStorage, moduleEntity: ModuleEntity, model: GradleModuleModel): GradleModuleModelEntity? =
  storage.resolve(GradleModuleModelEntityId(moduleEntity.symbolicId))?.let {
    storage.modifyGradleModuleModelEntity(it) {
      gradleModuleModel = model
    }
  }


val ModuleEntity.gradleModuleModel: GradleModuleModelEntity?
  by WorkspaceEntity.extension()