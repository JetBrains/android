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
package com.android.tools.idea.projectsystem.gradle

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.sync.idea.SyncContributorProjectContext
import com.android.tools.idea.gradle.project.sync.idea.createModuleEntity
import com.android.tools.idea.gradle.project.sync.idea.resolveHolderModuleName
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.entities
import com.intellij.workspaceModel.ide.legacyBridge.findModule
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncExtension
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import org.jetbrains.plugins.gradle.service.syncAction.impl.bridge.GradleBridgeEntitySource
import org.jetbrains.plugins.gradle.service.syncAction.virtualFileUrl

/**
 * This is a sync contributor that runs after the platform's content root contributor to fix-up any issues caused by it and makes sure
 * everything still works fine even when the bridge data service is removed.
 *
 * Long term plan is to fix all issues upstream and remove it, but I've opted for this implementation for the flexibility it provides until
 * the issues are fixed. It's worth pointing that there is a longer term plan to remove the bridge data service on the platform.
 */
@Suppress("UnstableApiUsage")
internal class FixSyncContributorIssues : GradleSyncExtension {

  override fun updateSyncStorage(
    context: ProjectResolverContext,
    syncStorage: MutableEntityStorage,
    projectStorage: ImmutableEntityStorage,
    phase: GradleSyncPhase,
  ) {
    if (!StudioFlags.PHASED_SYNC_BRIDGE_DATA_SERVICE_DISABLED.get()) {
      // If data bridge is not disabled, everything that was set up by phased sync will be removed, so no need to do anything.
      return
    }

    // Keep the root module as an iml based entity, because many things go wrong if there isn't at least one .iml based module
    removeGradleBasedEntitiesForRootModule(context, syncStorage)

    reconcileExistingHolderModules(context, syncStorage, phase)
  }

  private fun reconcileExistingHolderModules(context: ProjectResolverContext, storage: MutableEntityStorage, phase: GradleSyncPhase) {
    val entitiesByUrls = storage.entities(ContentRootEntity::class.java).associate { it.url to it.module }
    context.allBuilds.forEach { buildModel ->
      buildModel.projects.forEach { projectModel ->
        with(SyncContributorProjectContext(context, context.project, phase, buildModel, projectModel)) {
          // no need to reconcile the root module, or an existing holder module matching the expected name
          if (isGradleRootProject || storage.resolve(ModuleId(resolveHolderModuleName())) != null) return@forEach
          val existingModuleEntity = entitiesByUrls[projectEntitySource.projectRootUrl] ?: return@forEach
          existingModuleEntity.findModule(storage)?.getAllLinkedModules()?.forEach {
            it.putUserData(LINKED_ANDROID_GRADLE_MODULE_GROUP, null)
          }
          storage.removeEntity(existingModuleEntity)
          storage addEntity createModuleEntity(resolveHolderModuleName(), projectEntitySource)
        }
      }
    }
  }

  /**
   * We keep the root module as an iml based entity, because a lot of things go wrong if we don't.
   * Specifically [com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleBridgeLoaderService] currently disregards
   * the entire workspace model if there are no iml based entities at all.
   *
   * Also see for more context:
   * [com.intellij.workspaceModel.ide.impl.jps.serialization.JpsProjectModelSynchronizer.hasNoSerializedJpsModules]
   *
   * TODO(b/384022658): We should aim to delete this in the long term, but for now it should be fine to keep.
   */
  private fun removeGradleBasedEntitiesForRootModule(
    context: ProjectResolverContext,
    storage: MutableEntityStorage,
  ) {
    val projectRootUrl = context.virtualFileUrl(context.projectPath)
    storage.entities<ModuleEntity>()
      .filter { module -> module.entitySource is GradleBridgeEntitySource }
      .filter { module -> module.contentRoots.any { it.url == projectRootUrl } }
      .toList().forEach { module -> storage.removeEntity(module) }
  }
}


