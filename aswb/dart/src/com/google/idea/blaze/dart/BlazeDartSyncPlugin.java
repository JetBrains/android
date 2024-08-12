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
package com.google.idea.blaze.dart;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.plugin.PluginUtils;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.libraries.LibrarySource;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import java.util.Collection;
import java.util.Set;
import javax.annotation.Nullable;

/** Supports Dart. */
public class BlazeDartSyncPlugin implements BlazeSyncPlugin {

  private static final String DART_PLUGIN_ID = "Dart";

  @Override
  public Set<LanguageClass> getSupportedLanguagesInWorkspace(WorkspaceType workspaceType) {
    return ImmutableSet.of(LanguageClass.DART);
  }

  @Override
  public ImmutableList<String> getRequiredExternalPluginIds(Collection<LanguageClass> languages) {
    return languages.contains(LanguageClass.DART)
        ? ImmutableList.of(DART_PLUGIN_ID)
        : ImmutableList.of();
  }

  @Override
  public ImmutableList<WorkspaceType> getSupportedWorkspaceTypes() {
    return ImmutableList.of(WorkspaceType.DART);
  }

  @Nullable
  @Override
  public ModuleType<?> getWorkspaceModuleType(WorkspaceType workspaceType) {
    return workspaceType == WorkspaceType.DART
        ? ModuleTypeManager.getInstance().getDefaultModuleType()
        : null;
  }

  /**
   * After the Blaze Sync process, all libraries are cleared out, this method adds the Library back
   * to the workspace model.
   */
  @Override
  public void updateProjectStructure(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      @Nullable BlazeProjectData oldBlazeProjectData,
      ModuleEditor moduleEditor,
      Module workspaceModule,
      ModifiableRootModel workspaceModifiableModel) {
    if (!blazeProjectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.DART)) {
      return;
    }

    Library dartSdkLibrary = DartSdkUtils.findDartLibrary(project);
    if (dartSdkLibrary != null
        && workspaceModifiableModel.findLibraryOrderEntry(dartSdkLibrary) == null) {
      workspaceModifiableModel.addLibraryEntry(dartSdkLibrary);
    }
    // At this point, this extension used to create an error, "Dart language support is requested,
    // but the Dart SDK was not found." when no Dart SDK was found, i.e. dartSdkLibrary == null,
    // however the Dart Plugin provide these notifications already.
  }

  @Override
  public boolean validate(
      Project project, BlazeContext context, BlazeProjectData blazeProjectData) {
    if (!blazeProjectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.DART)) {
      return true;
    }
    if (!PluginUtils.isPluginEnabled(DART_PLUGIN_ID)) {
      IssueOutput.error(
              "The Dart plugin is required for Dart support. "
                  + "Click here to install/enable the plugin and restart")
          .navigatable(PluginUtils.installOrEnablePluginNavigable(DART_PLUGIN_ID))
          .submit(context);
      return false;
    }
    return true;
  }

  @Nullable
  @Override
  public LibrarySource getLibrarySource(
      ProjectViewSet projectViewSet, BlazeProjectData blazeProjectData) {
    if (!blazeProjectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.DART)) {
      return null;
    }
    return BlazeDartLibrarySource.INSTANCE;
  }
}
