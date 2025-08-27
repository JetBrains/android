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

import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child

interface GradleAndroidModelEntity: WorkspaceEntity {
  val module: ModuleEntity
  val gradleAndroidModel: GradleAndroidModel

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<GradleAndroidModelEntity> {
    override var entitySource: EntitySource
    var module: ModuleEntity.Builder
    var gradleAndroidModel: GradleAndroidModel
  }

  companion object : EntityType<GradleAndroidModelEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      gradleAndroidModel: GradleAndroidModel,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.gradleAndroidModel = gradleAndroidModel
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyGradleAndroidModelEntity(
  entity: GradleAndroidModelEntity,
  modification: GradleAndroidModelEntity.Builder.() -> Unit,
): GradleAndroidModelEntity {
  return modifyEntity(GradleAndroidModelEntity.Builder::class.java, entity, modification)
}

var ModuleEntity.Builder.gradleAndroidModel: GradleAndroidModelEntity.Builder?
  by WorkspaceEntity.extensionBuilder(GradleAndroidModelEntity::class.java)
//endregion


val ModuleEntity.gradleAndroidModel: @Child GradleAndroidModelEntity?
  by WorkspaceEntity.extension()
