/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure

import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.project.model.GradleModuleModel
import com.android.tools.idea.gradle.project.model.JavaModuleModel
import com.android.tools.idea.gradle.structure.model.PsResolvedModuleModel
import com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID
import com.android.tools.idea.gradle.util.GradleUtil.getGradleExecutionSettings
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter.NULL_OBJECT
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.RESOLVE_PROJECT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.ide.PooledThreadExecutor
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolver

class GradleResolver {
  private val myProjectResolver = GradleProjectResolver()

  // TODO(b/110411567): Rework to make it compatible with the new sync.
  /**
   * Request Gradle sync models without updating IDE projects and returns the [ListenableFuture] of the requested models.
   */
  fun requestProjectResolved(project: Project, disposable: Disposable): ListenableFuture<List<PsResolvedModuleModel>> {
    val settings = getGradleExecutionSettings(project)
    val id = ExternalSystemTaskId.create(GRADLE_SYSTEM_ID, RESOLVE_PROJECT, project)
    val projectPath = project.basePath!!

    return MoreExecutors.listeningDecorator(PooledThreadExecutor.INSTANCE).submit<List<PsResolvedModuleModel>> {
      myProjectResolver.resolveProjectInfo(id, projectPath, false, settings, NULL_OBJECT)!!
        .children
        .mapNotNull { module ->
          module.children.mapNotNull { it.data as? GradleModuleModel }.firstOrNull()?.let { it.gradlePath to module }
        }
        .flatMap { (gradlePath, module) ->
          module
            .takeIf { it.data is ModuleData }
            ?.let {
              it.children
                .map { it.data }
                .mapNotNull {
                  when (it) {
                    is AndroidModuleModel -> PsResolvedModuleModel.PsAndroidModuleResolvedModel(gradlePath, it)
                    is JavaModuleModel -> PsResolvedModuleModel.PsJavaModuleResolvedModel(gradlePath, it)
                    else -> null
                  }
                }
            }
            .orEmpty()
        }

    }.also {
      Disposer.register(disposable, Disposable { it.cancel(true) })
    }
  }
}
