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
@file:JvmName("GradleProjectPathEntityModifications")

package com.android.tools.idea.gradle.project.entities

import com.android.tools.idea.gradle.project.entities.impl.GradleProjectPathEntityImpl
import com.android.tools.idea.projectsystem.gradle.GradleProjectPath
import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder

@GeneratedCodeApiVersion(3)
interface GradleProjectPathEntityBuilder : WorkspaceEntityBuilder<GradleProjectPathEntity> {
  override var entitySource: EntitySource
  var module: ModuleEntityBuilder
  var gradleProjectPath: GradleProjectPath
}

internal object GradleProjectPathEntityType : EntityType<GradleProjectPathEntity, GradleProjectPathEntityBuilder>() {
  override val entityImplClass: Class<*> get() = GradleProjectPathEntityImpl::class.java
  override val entityImplBuilderClass: Class<*> get() = GradleProjectPathEntityImpl.Builder::class.java
  operator fun invoke(
    gradleProjectPath: GradleProjectPath,
    entitySource: EntitySource,
    init: (GradleProjectPathEntityBuilder.() -> Unit)? = null,
  ): GradleProjectPathEntityBuilder {
    val builder = builder()
    builder.gradleProjectPath = gradleProjectPath
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyGradleProjectPathEntity(
  entity: GradleProjectPathEntity,
  modification: GradleProjectPathEntityBuilder.() -> Unit,
): GradleProjectPathEntity = modifyEntity(GradleProjectPathEntityBuilder::class.java, entity, modification)

var ModuleEntityBuilder.gradleProjectPath: GradleProjectPathEntityBuilder?
  by WorkspaceEntity.extensionBuilder(GradleProjectPathEntity::class.java)

@JvmOverloads
@JvmName("createGradleProjectPathEntity")
fun GradleProjectPathEntity(
  gradleProjectPath: GradleProjectPath,
  entitySource: EntitySource,
  init: (GradleProjectPathEntityBuilder.() -> Unit)? = null,
): GradleProjectPathEntityBuilder = GradleProjectPathEntityType(gradleProjectPath, entitySource, init)
