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
package com.android.tools.idea.gradle.project.entities

import com.android.tools.idea.projectsystem.gradle.GradleProjectPath
import com.android.tools.idea.projectsystem.gradle.GradleSourceSetProjectPath
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.annotations.Parent

interface GradleProjectPathEntity : WorkspaceEntityWithSymbolicId {
  @Parent val module: ModuleEntity
  val gradleProjectPath: GradleProjectPath
  override val symbolicId: GradleProjectPathSymbolicId
    get() = GradleProjectPathSymbolicId(gradleProjectPath)
}

data class GradleProjectPathSymbolicId(val gradleProjectPath: GradleProjectPath) : SymbolicEntityId<GradleProjectPathEntity> {
  override val presentableName: String
    get() =
      listOfNotNull(
          gradleProjectPath.buildRoot,
          gradleProjectPath.path,
          (gradleProjectPath as? GradleSourceSetProjectPath)?.sourceSet?.sourceSetName,
        )
        .joinToString()
}

object GradleProjectPathEntitySource : EntitySource

internal val ModuleEntity.gradleProjectPath: GradleProjectPathEntity? by WorkspaceEntity.extension()
