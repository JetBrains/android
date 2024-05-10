/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.quickFixes

import com.android.tools.idea.gradle.project.sync.idea.issues.DescribedBuildIssueQuickFix
import com.android.tools.idea.projectsystem.gradle.GradleHolderProjectPath
import com.android.tools.idea.projectsystem.gradle.resolveIn
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.lang.JavaVersion
import java.util.concurrent.CompletableFuture

class SetJavaToolchainQuickFix(
  val versionToSet: Int,
  val gradleModules: List<String>
) : DescribedBuildIssueQuickFix {
  override val description: String = "Set Java Toolchain to $versionToSet"
  override val id: String = "set.java.toolchain.$versionToSet"

  override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
    val future = CompletableFuture<Any>()
    try {
      if (!project.isDisposed) {
        val projectRootPath = project.guessProjectDir()?.path
        val modules = gradleModules.mapNotNull { modulePath ->
          projectRootPath ?: return@mapNotNull null
          GradleHolderProjectPath(FileUtil.toSystemIndependentName(projectRootPath), modulePath).resolveIn(project)
        }
        val processor = AddJavaToolchainDefinition(project, versionToSet, modules)
        processor.setPreviewUsages(true)
        processor.run()
      }
      future.complete(null)
    }
    catch (e: Exception) {
      future.completeExceptionally(e)
    }
    return future
  }
}

