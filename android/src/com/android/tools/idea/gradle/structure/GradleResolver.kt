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

  fun requestProjectResolved(project: Project, disposable: Disposable): ListenableFuture<Unit> {
    val settings = getGradleExecutionSettings(project)
    val id = ExternalSystemTaskId.create(GRADLE_SYSTEM_ID, RESOLVE_PROJECT, project)
    val projectPath = project.basePath!!

    return MoreExecutors.listeningDecorator(PooledThreadExecutor.INSTANCE).submit<Unit> {
      myProjectResolver.resolveProjectInfo(id, projectPath, false, settings, NULL_OBJECT)!!
    }.also {
      Disposer.register(disposable, Disposable { it.cancel(true) })
    }
  }
}
