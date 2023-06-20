/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model

import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.model.NdkModuleModel
import com.android.tools.idea.gradle.project.sync.issues.SyncIssues
import com.intellij.openapi.externalSystem.model.project.dependencies.ProjectDependencies
import org.jetbrains.plugins.gradle.model.ExternalProject

/**
 * A sealed wrapper around a gradle model of a module identified by its gradle path.
 */
sealed class PsResolvedModuleModel {
  abstract val gradlePath: String
  abstract val buildFile: String?

  data class PsAndroidModuleResolvedModel(
    override val gradlePath: String,
    override val buildFile: String?,
    val model: GradleAndroidModel,
    val nativeModel: NdkModuleModel?,
    val syncIssues: SyncIssues
  ) : PsResolvedModuleModel()

  data class PsJavaModuleResolvedModel(
    override val gradlePath: String,
    override val buildFile: String?,
    val model: ExternalProject,
    val dependencies: ProjectDependencies?,
    val syncIssues: SyncIssues
  ) : PsResolvedModuleModel()
}