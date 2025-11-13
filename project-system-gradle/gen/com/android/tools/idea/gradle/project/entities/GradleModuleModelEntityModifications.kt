// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleModuleModelEntityModifications")

package com.android.tools.idea.gradle.project.entities

import com.android.tools.idea.gradle.project.model.GradleModuleModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.annotations.Parent

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
