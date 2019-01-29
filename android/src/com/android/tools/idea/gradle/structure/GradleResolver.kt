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
import com.android.tools.idea.gradle.project.sync.GradleModuleModels
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.structure.model.PsResolvedModuleModel
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.ide.PooledThreadExecutor

class GradleResolver {
  /**
   * Requests Gradle models without updating IDE projects and returns the [ListenableFuture] of the requested models.
   */
  fun requestProjectResolved(project: Project, disposable: Disposable): ListenableFuture<List<PsResolvedModuleModel>> =
    MoreExecutors.listeningDecorator(PooledThreadExecutor.INSTANCE).submit<List<PsResolvedModuleModel>> {
      GradleSyncInvoker
        .getInstance()
        .fetchGradleModels(project, EmptyProgressIndicator())
        .mapNotNull { findModel(it) }
    }.also {
      Disposer.register(disposable, Disposable { it.cancel(true) })
    }
}

private fun findModel(module: GradleModuleModels): PsResolvedModuleModel? {
  val gradlePath = (module.findModel(GradleModuleModel::class.java) ?: return null).gradlePath
  return module.findModel(AndroidModuleModel::class.java)?.let { PsResolvedModuleModel.PsAndroidModuleResolvedModel(gradlePath, it) }
         ?: module.findModel(JavaModuleModel::class.java)?.let { PsResolvedModuleModel.PsJavaModuleResolvedModel(gradlePath, it) }
}
