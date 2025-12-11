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
package com.google.idea.blaze.base.sync;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import java.util.Set;
import javax.annotation.Nullable;

/** Can plug into the blaze sync system. */
public interface BlazeSyncPlugin {
  ExtensionPointName<BlazeSyncPlugin> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.SyncPlugin");

  /**
   * The {@link WorkspaceType}s supported by this plugin. Not used to choose the project's
   * WorkspaceType.
   */
  default ImmutableList<WorkspaceType> getSupportedWorkspaceTypes() {
    return ImmutableList.of();
  }

  /**
   * @return The default workspace type recommended by this plugin.
   */
  @Nullable
  default WorkspaceType getDefaultWorkspaceType() {
    return null;
  }

  /**
   * @return The set of supported languages under this workspace type.
   */
  default Set<LanguageClass> getSupportedLanguagesInWorkspace(WorkspaceType workspaceType) {
    return ImmutableSet.of();
  }

  /** Validates the project. */
  default boolean validate(
      Project project, BlazeContext context, BlazeProjectData blazeProjectData) {
    return true;
  }

  /**
   * Validates the project view.
   *
   * @param project null when called from the project import wizard
   * @return True for success, false for fatal error.
   */
  default boolean validateProjectView(
      @Nullable Project project,
      BlazeContext context,
      ProjectViewSet projectViewSet,
      WorkspaceLanguageSettings workspaceLanguageSettings) {
    return true;
  }

  /** Returns any custom sections that this plugin supports. */
  default Collection<SectionParser> getSections() {
    return ImmutableList.of();
  }
}
