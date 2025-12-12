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
@file:JvmName("GradleAndroidModelEntityModifications")

package com.android.tools.idea.gradle.project.entities

import com.android.tools.idea.gradle.model.impl.IdeLibraryModelResolverImpl
import com.android.tools.idea.gradle.model.impl.IdeVariantImpl
import com.android.tools.idea.gradle.project.model.GradleAndroidDependencyModel
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.model.GradleAndroidModelImpl
import com.intellij.openapi.module.Module
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.ExternalMappingKey
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntity

@GeneratedCodeApiVersion(3)
interface GradleAndroidModelEntityBuilder : WorkspaceEntityBuilder<GradleAndroidModelEntity> {
  override var entitySource: EntitySource
  var module: ModuleEntityBuilder
  var gradleAndroidModel: GradleAndroidModelImpl
  var resolvedVariant: IdeVariantImpl?
}

internal object GradleAndroidModelEntityType : EntityType<GradleAndroidModelEntity, GradleAndroidModelEntityBuilder>() {
  override val entityClass: Class<GradleAndroidModelEntity> get() = GradleAndroidModelEntity::class.java
  operator fun invoke(
    gradleAndroidModel: GradleAndroidModelImpl,
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
  gradleAndroidModel: GradleAndroidModelImpl,
  entitySource: EntitySource,
  init: (GradleAndroidModelEntityBuilder.() -> Unit)? = null,
): GradleAndroidModelEntityBuilder = GradleAndroidModelEntityType(gradleAndroidModel, entitySource, init)
