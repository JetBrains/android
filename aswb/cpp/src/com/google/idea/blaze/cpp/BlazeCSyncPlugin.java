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
package com.google.idea.blaze.cpp;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.SyncMode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.util.Set;

final class BlazeCSyncPlugin implements BlazeSyncPlugin {
  @Override
  public Set<LanguageClass> getSupportedLanguagesInWorkspace(WorkspaceType workspaceType) {
    if (workspaceType == WorkspaceType.C) {
      return ImmutableSet.of(LanguageClass.C);
    }
    return ImmutableSet.of();
  }

  @Override
  public void updateInMemoryState(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      Module workspaceModule,
      SyncMode syncMode) {
    if (!blazeProjectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.C)) {
      return;
    }

    Scope.push(
        context,
        childContext -> {
          childContext.push(new TimingScope("Setup C Workspace", EventType.Other));

          ApplicationManager.getApplication()
              .runReadAction(
                  () -> {
                    BlazeCWorkspace blazeCWorkspace = BlazeCWorkspace.getInstance(project);
                    blazeCWorkspace.update(
                        childContext, workspaceRoot, projectViewSet, blazeProjectData, syncMode);
                  });
        });
  }

  @Override
  public boolean refreshExecutionRoot(Project project, BlazeProjectData blazeProjectData) {
    return blazeProjectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.C);
  }
}
