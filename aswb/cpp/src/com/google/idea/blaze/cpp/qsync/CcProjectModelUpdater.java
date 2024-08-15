/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.cpp.qsync;

import com.google.idea.blaze.base.qsync.QuerySyncProject;
import com.google.idea.blaze.base.qsync.QuerySyncProjectListenerProvider;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.qsync.QuerySyncProjectListener;
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot;
import com.google.idea.blaze.qsync.project.ProjectPath;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.jetbrains.cidr.lang.workspace.OCWorkspace;

/**
 * A {@link QuerySyncProjectListener} that triggers the update of the IJ project model with CC
 * compilation information from the project proto.
 */
public class CcProjectModelUpdater implements QuerySyncProjectListener {

  /** Provides instances of {@link CcProjectModelUpdater}. Instantiated as an extension by IJ. */
  public static class Provider implements QuerySyncProjectListenerProvider {
    public Provider() {}

    @Override
    public QuerySyncProjectListener createListener(QuerySyncProject querySyncProject) {
      return create(querySyncProject.getIdeProject(), querySyncProject.getProjectPathResolver());
    }
  }

  private final ProjectPath.Resolver pathResolver;
  private final OCWorkspace readonlyOcWorkspace;
  private final Project project;

  public static CcProjectModelUpdater create(Project project, ProjectPath.Resolver pathResolver) {
    return new CcProjectModelUpdater(pathResolver, OCWorkspace.getInstance(project), project);
  }

  public CcProjectModelUpdater(
      ProjectPath.Resolver pathResolver, OCWorkspace ocWorkspace, Project project) {
    this.pathResolver = pathResolver;
    this.readonlyOcWorkspace = ocWorkspace;
    this.project = project;
  }

  @Override
  public void onNewProjectSnapshot(Context<?> context, QuerySyncProjectSnapshot instance) {
    updateProjectModel(instance.project(), context);
  }

  public void updateProjectModel(ProjectProto.Project spec, Context<?> context) {
    if (!spec.hasCcWorkspace()) {
      return;
    }

    // TODO(b/307720763) Check & rationalise the update here, to ensure the project does not
    //   get out of sync.
    CcProjectModelUpdateOperation updateOp =
        new CcProjectModelUpdateOperation(context, readonlyOcWorkspace, pathResolver);
    try {
      updateOp.visitWorkspace(spec.getCcWorkspace());
      updateOp.preCommit();
      WriteAction.runAndWait(
          () -> {
            if (!project.isDisposed()) {
              updateOp.commit();
            }
          });
    } finally {
      Disposer.dispose(updateOp);
    }
  }
}
