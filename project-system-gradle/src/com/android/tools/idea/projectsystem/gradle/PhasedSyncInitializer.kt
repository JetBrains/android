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
import com.android.tools.idea.gradle.project.sync.idea.resolveModuleName
import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.openapi.externalSystem.util.Order
import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.workspaceModel.ide.legacyBridge.findModule
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncContributor

/**
 * This is a sync contributor that runs after the platform's content root contributor to fix-up any issues caused by it and makes sure
 * everything still works fine even when the bridge data service is removed.
 *
 * Long term plan is to fix all issues upstream and remove it, but I've opted for this implementation for the flexibility it provides until
 * the issues are fixed. It's worth pointing that there is a longer term plan to remove the bridge data service on the platform.
 */
@Suppress("UnstableApiUsage")
@Order(GradleSyncContributor.Order.CONTENT_ROOT_CONTRIBUTOR + 1)
class FixSyncContributorIssues: GradleSyncContributor {
  override suspend fun onModelFetchPhaseCompleted(context: ProjectResolverContext,
                                                  storage: MutableEntityStorage,
                                                  phase: GradleModelFetchPhase) {
    if (!context.isPhasedSyncEnabled || !StudioFlags.PHASED_SYNC_BRIDGE_DATA_SERVICE_DISABLED.get()) {
      // If data bridge is not disabled, everything that was set up by phased sync will be removed, so no need to do anything.
      return
    }

    if (phase == GradleModelFetchPhase.PROJECT_MODEL_PHASE) {
      reconcileExistingHolderModules(context, context.project, storage)
    }
  }

  private fun reconcileExistingHolderModules(context: ProjectResolverContext, project: Project, storage: MutableEntityStorage) {
    val entitiesByUrls = storage.entities(ContentRootEntity::class.java).associate { it.url to it.module }
    context.allBuilds.forEach { buildModel ->
      buildModel.projects.forEach { projectModel ->
        with(SyncContributorProjectContext(context, project, buildModel, projectModel)) {
          // no need to reconcile the root module, or an existing holder module matching the expected name
          if (isGradleRootProject || storage.resolve(ModuleId(resolveModuleName())) != null) return@forEach
          val existingModuleEntity = entitiesByUrls[projectEntitySource.projectRootUrl] ?: return@forEach
          existingModuleEntity.findModule(storage)?.getAllLinkedModules()?.forEach {
            it.putUserData(LINKED_ANDROID_GRADLE_MODULE_GROUP, null)
          }
          storage.removeEntity(existingModuleEntity)
          storage addEntity createModuleEntity(resolveModuleName(), projectEntitySource)
        }
      }
    }
  }
}


