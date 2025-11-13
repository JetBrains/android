// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleAndroidModelEntityModifications")

package com.android.tools.idea.gradle.project.entities

import com.android.tools.idea.gradle.project.model.GradleAndroidModel
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
interface GradleAndroidModelEntityBuilder : WorkspaceEntityBuilder<GradleAndroidModelEntity> {
  override var entitySource: EntitySource
  var module: ModuleEntityBuilder
  var gradleAndroidModel: GradleAndroidModel
}

internal object GradleAndroidModelEntityType : EntityType<GradleAndroidModelEntity, GradleAndroidModelEntityBuilder>() {
  override val entityClass: Class<GradleAndroidModelEntity> get() = GradleAndroidModelEntity::class.java
  operator fun invoke(
    gradleAndroidModel: GradleAndroidModel,
    entitySource: EntitySource,
    init: (GradleAndroidModelEntityBuilder.() -> Unit)? = null,
  ): GradleAndroidModelEntityBuilder {
    val builder = builder()
    builder.gradleAndroidModel = gradleAndroidModel
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyGradleAndroidModelEntity(
  entity: GradleAndroidModelEntity,
  modification: GradleAndroidModelEntityBuilder.() -> Unit,
): GradleAndroidModelEntity = modifyEntity(GradleAndroidModelEntityBuilder::class.java, entity, modification)

var ModuleEntityBuilder.gradleAndroidModel: GradleAndroidModelEntityBuilder?
  by WorkspaceEntity.extensionBuilder(GradleAndroidModelEntity::class.java)


@JvmOverloads
@JvmName("createGradleAndroidModelEntity")
fun GradleAndroidModelEntity(
  gradleAndroidModel: GradleAndroidModel,
  entitySource: EntitySource,
  init: (GradleAndroidModelEntityBuilder.() -> Unit)? = null,
): GradleAndroidModelEntityBuilder = GradleAndroidModelEntityType(gradleAndroidModel, entitySource, init)
