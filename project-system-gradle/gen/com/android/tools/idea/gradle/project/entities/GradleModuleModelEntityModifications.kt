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
@file:JvmName("GradleModuleModelEntityModifications")

package com.android.tools.idea.gradle.project.entities

import com.android.tools.idea.gradle.project.model.GradleModuleModel
import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder

@GeneratedCodeApiVersion(3)
interface GradleModuleModelEntityBuilder : WorkspaceEntityBuilder<GradleModuleModelEntity> {
  override var entitySource: EntitySource
  var module: ModuleEntityBuilder
  var gradleModuleModel: GradleModuleModel
}

internal object GradleModuleModelEntityType : EntityType<GradleModuleModelEntity, GradleModuleModelEntityBuilder>() {
  override val entityClass: Class<GradleModuleModelEntity> get() = GradleModuleModelEntity::class.java
  operator fun invoke(
    gradleModuleModel: GradleModuleModel,
    entitySource: EntitySource,
    init: (GradleModuleModelEntityBuilder.() -> Unit)? = null,
  ): GradleModuleModelEntityBuilder {
    val builder = builder()
    builder.gradleModuleModel = gradleModuleModel
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyGradleModuleModelEntity(
  entity: GradleModuleModelEntity,
  modification: GradleModuleModelEntityBuilder.() -> Unit,
): GradleModuleModelEntity = modifyEntity(GradleModuleModelEntityBuilder::class.java, entity, modification)

var ModuleEntityBuilder.gradleModuleModel: GradleModuleModelEntityBuilder?
  by WorkspaceEntity.extensionBuilder(GradleModuleModelEntity::class.java)


@JvmOverloads
@JvmName("createGradleModuleModelEntity")
fun GradleModuleModelEntity(
  gradleModuleModel: GradleModuleModel,
  entitySource: EntitySource,
  init: (GradleModuleModelEntityBuilder.() -> Unit)? = null,
): GradleModuleModelEntityBuilder = GradleModuleModelEntityType(gradleModuleModel, entitySource, init)
