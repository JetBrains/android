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
package com.android.tools.idea.gradle.project.entities.impl

import com.android.tools.idea.gradle.project.entities.GradleModuleModelEntity
import com.android.tools.idea.gradle.project.model.GradleModuleModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.extractOneToOneParent
import com.intellij.platform.workspace.storage.impl.updateOneToOneParentOfChild
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class GradleModuleModelEntityImpl(private val dataSource: GradleModuleModelEntityData) : GradleModuleModelEntity,
                                                                                                  WorkspaceEntityBase(dataSource) {

  private companion object {
    internal val MODULE_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ModuleEntity::class.java, GradleModuleModelEntity::class.java, ConnectionId.ConnectionType.ONE_TO_ONE, false)

    private val connections = listOf<ConnectionId>(
      MODULE_CONNECTION_ID,
    )

  }

  override val module: ModuleEntity
    get() = snapshot.extractOneToOneParent(MODULE_CONNECTION_ID, this)!!

  override val gradleModuleModel: GradleModuleModel
    get() {
      readField("gradleModuleModel")
      return dataSource.gradleModuleModel
    }

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: GradleModuleModelEntityData?) :
    ModifiableWorkspaceEntityBase<GradleModuleModelEntity, GradleModuleModelEntityData>(result), GradleModuleModelEntity.Builder {
    internal constructor() : this(GradleModuleModelEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        } else {
          error("Entity GradleModuleModelEntity is already created in a different builder")
        }
      }

      this.diff = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
      // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
      // Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null

      // Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    private fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (_diff != null) {
        if (_diff.extractOneToOneParent<WorkspaceEntityBase>(MODULE_CONNECTION_ID, this) == null) {
          error("Field GradleModuleModelEntity#module should be initialized")
        }
      } else {
        if (this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] == null) {
          error("Field GradleModuleModelEntity#module should be initialized")
        }
      }
      if (!getEntityData().isGradleModuleModelInitialized()) {
        error("Field GradleModuleModelEntity#gradleModuleModel should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as GradleModuleModelEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.gradleModuleModel != dataSource.gradleModuleModel) this.gradleModuleModel = dataSource.gradleModuleModel
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var module: ModuleEntity.Builder
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(MODULE_CONNECTION_ID, this) as? ModuleEntity.Builder)
            ?: (this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)]!! as ModuleEntity.Builder)
        } else {
          this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)]!! as ModuleEntity.Builder
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToOneParentOfChild(MODULE_CONNECTION_ID, this, value)
        } else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] = this
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] = value
        }
        changedProperty.add("module")
      }

    override var gradleModuleModel: GradleModuleModel
      get() = getEntityData().gradleModuleModel
      set(value) {
        checkModificationAllowed()
        getEntityData(true).gradleModuleModel = value
        changedProperty.add("gradleModuleModel")

      }

    override fun getEntityClass(): Class<GradleModuleModelEntity> = GradleModuleModelEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class GradleModuleModelEntityData : WorkspaceEntityData<GradleModuleModelEntity>() {
  lateinit var gradleModuleModel: GradleModuleModel

  internal fun isGradleModuleModelInitialized(): Boolean = ::gradleModuleModel.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<GradleModuleModelEntity> {
    val modifiable = GradleModuleModelEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): GradleModuleModelEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = GradleModuleModelEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.android.tools.idea.gradle.project.entities.GradleModuleModelEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return GradleModuleModelEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return GradleModuleModelEntity(gradleModuleModel, entitySource) {
      parents.filterIsInstance<ModuleEntity.Builder>().singleOrNull()?.let { this.module = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(ModuleEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as GradleModuleModelEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.gradleModuleModel != other.gradleModuleModel) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as GradleModuleModelEntityData

    if (this.gradleModuleModel != other.gradleModuleModel) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + gradleModuleModel.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + gradleModuleModel.hashCode()
    return result
  }
}
