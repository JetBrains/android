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
package com.google.idea.blaze.base.qsync;

import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.common.Context;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.util.Set;

/** Can plug into the blaze sync system. */
public interface BlazeQuerySyncPlugin {
  ExtensionPointName<BlazeQuerySyncPlugin> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.QuerySyncPlugin");

  /** Updates the sdk and language settings for the project */
  default void updateProjectSettingsForQuerySync(
      Project project, Context<?> context, ProjectViewSet projectViewSet) {}

  /** Modifies the IDE project structure */
  default void updateProjectStructureForQuerySync(
      Project project,
      Context<?> context,
      IdeModifiableModelsProvider models,
      WorkspaceRoot workspaceRoot,
      Module workspaceModule,
      Set<String> androidResourceDirectories,
      Set<String> androidSourcePackages,
      WorkspaceLanguageSettings workspaceLanguageSettings) {}
}
