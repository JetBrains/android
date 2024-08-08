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
package com.google.idea.blaze.plugin.sync;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.SourceFolderProvider;
import com.google.idea.blaze.java.sync.JavaLanguageLevelHelper;
import com.google.idea.blaze.java.sync.model.BlazeJavaSyncData;
import com.google.idea.blaze.java.sync.projectstructure.JavaSourceFolderProvider;
import com.google.idea.common.util.Transactions;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Development environment support for intellij plugin projects. Prevents the project SDK being
 * reset during sync
 */
public class IntellijPluginSyncPlugin implements BlazeSyncPlugin {

  @Override
  public ImmutableList<WorkspaceType> getSupportedWorkspaceTypes() {
    return ImmutableList.of(WorkspaceType.INTELLIJ_PLUGIN);
  }

  @Nullable
  @Override
  public ModuleType<?> getWorkspaceModuleType(WorkspaceType workspaceType) {
    if (workspaceType == WorkspaceType.INTELLIJ_PLUGIN) {
      return StdModuleTypes.JAVA;
    }
    return null;
  }

  @Override
  public Set<LanguageClass> getSupportedLanguagesInWorkspace(WorkspaceType workspaceType) {
    if (workspaceType == WorkspaceType.INTELLIJ_PLUGIN) {
      return ImmutableSet.of(LanguageClass.JAVA);
    }
    return ImmutableSet.of();
  }

  @Nullable
  @Override
  public SourceFolderProvider getSourceFolderProvider(BlazeProjectData projectData) {
    if (!projectData
        .getWorkspaceLanguageSettings()
        .isWorkspaceType(WorkspaceType.INTELLIJ_PLUGIN)) {
      return null;
    }
    return new JavaSourceFolderProvider(projectData.getSyncState().get(BlazeJavaSyncData.class));
  }

  @Override
  public void updateProjectSdk(
      Project project,
      BlazeContext context,
      ProjectViewSet projectViewSet,
      BlazeVersionData blazeVersionData,
      BlazeProjectData blazeProjectData) {
    if (!blazeProjectData
        .getWorkspaceLanguageSettings()
        .isWorkspaceType(WorkspaceType.INTELLIJ_PLUGIN)) {
      return;
    }

    LanguageLevel javaLanguageLevel =
        JavaLanguageLevelHelper.getJavaLanguageLevel(projectViewSet, blazeProjectData);

    // Leave the SDK, but set the language level
    Transactions.submitWriteActionTransactionAndWait(
        () -> {
          LanguageLevelProjectExtension ext = LanguageLevelProjectExtension.getInstance(project);
          ext.setLanguageLevel(javaLanguageLevel);
        });
  }
}
