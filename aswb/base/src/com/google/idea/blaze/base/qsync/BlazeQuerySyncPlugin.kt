/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.qsync

import com.google.idea.blaze.base.model.primitives.WorkspaceRoot
import com.google.idea.blaze.base.projectview.ProjectViewSet
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings
import com.google.idea.blaze.common.Context
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

/** Can plug into the blaze sync system.  */
interface BlazeQuerySyncPlugin {
  /** Updates the sdk and language settings for the project  */
  fun updateProjectSettingsForQuerySync(project: Project, context: Context<*>, projectViewSet: ProjectViewSet ) = Unit

  /** Modifies the IDE project structure  */
  fun updateProjectStructureForQuerySync(
    project: Project,
    context: Context<*>,
    models: IdeModifiableModelsProvider,
    workspaceRoot: WorkspaceRoot,
    workspaceModule: Module,
    androidResourceDirectories: Set<String>,
    androidSourcePackages: Set<String>,
    workspaceLanguageSettings: WorkspaceLanguageSettings
  ) = Unit

  companion object {
    val EP_NAME: ExtensionPointName<BlazeQuerySyncPlugin> =
      ExtensionPointName.create<BlazeQuerySyncPlugin>("com.google.idea.blaze.QuerySyncPlugin")
  }
}
