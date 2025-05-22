package com.google.idea.blaze.base.qsync;

import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.intellij.openapi.util.ModificationTracker;
import org.jetbrains.annotations.Nullable;

public interface ProjectLoader {
  /**
   * A pre-processed definition of a project to be loaded.
   */
  record ProjectToLoadDefinition(
    WorkspaceRoot workspaceRoot,
    BuildSystem buildSystem,
    ProjectDefinition definition,
    ProjectViewSet projectViewSet,
    WorkspaceLanguageSettings workspaceLanguageSettings) {
  }

  /**
   * Loads a project definition from the import settings and the .bazelproject file.
   */
  ProjectToLoadDefinition loadProjectDefinition(BlazeContext context) throws BuildException;

  @Nullable QuerySyncProject loadProject(BlazeContext context) throws BuildException;

  ModificationTracker getProjectModificationTracker();
}
