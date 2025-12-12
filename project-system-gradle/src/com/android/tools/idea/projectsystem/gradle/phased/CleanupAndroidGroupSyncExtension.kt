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
package com.android.tools.idea.projectsystem.gradle.phased

import com.android.tools.idea.gradle.project.sync.idea.AndroidGradleSourceSetEntitySource
import com.android.tools.idea.projectsystem.gradle.LINKED_ANDROID_GRADLE_MODULE_GROUP
import com.intellij.openapi.externalSystem.util.Order
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.workspaceModel.ide.legacyBridge.findModule
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncExtension
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import org.jetbrains.plugins.gradle.service.syncAction.impl.extensions.GradleBaseSyncExtension

/** When sync is doing the clean-up of modules from previous sync, it's possible the holder module
 * gets deleted, and we are left with dangling source set module. This undesirable and breaks certain
 * project system APIs. Here if we detect such a case we also delete the dangling modules. */
@Order(GradleBaseSyncExtension.ORDER + 1)
class CleanupAndroidGroupSyncExtension : GradleSyncExtension {

  override fun updateProjectModel(
    context: ProjectResolverContext,
    syncStorage: MutableEntityStorage,
    projectStorage: MutableEntityStorage,
    phase: GradleSyncPhase,
  ) {
    if (phase == GradleSyncPhase.PROJECT_MODEL_PHASE) {
      projectStorage
        .entitiesBySource { it is AndroidGradleSourceSetEntitySource }
        .filterIsInstance<ModuleEntity>()
        .toList() // Materialized to avoid iterating over children entities of the removed modules
        .forEach { moduleEntity ->
          val androidGroup = moduleEntity.findModule(projectStorage)
            ?.getUserData(LINKED_ANDROID_GRADLE_MODULE_GROUP) ?: return@forEach
          // If the holder module can't be resolved, make sure to delete this module as well.
          if (projectStorage.resolve(ModuleId(androidGroup.holder.moduleName)) == null) {
            projectStorage.removeEntity(moduleEntity)
          }
      }
    }
  }
}