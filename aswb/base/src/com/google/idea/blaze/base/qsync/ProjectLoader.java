package com.google.idea.blaze.base.qsync;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.project.ProjectDirectoryConfigurator;
import com.intellij.openapi.project.Project;

public interface ProjectLoader {
  /**
   * A pre-processed definition of a project to be loaded.
   */
  record ProjectToLoadDefinition(
    WorkspaceRoot workspaceRoot,
    ProjectDirectoryConfigurator projectDirectoryConfigurator,
    BuildSystem buildSystem,
    ProjectDefinition definition,
    WorkspaceLanguageSettings workspaceLanguageSettings,
    QuerySyncLanguageSettings languageSettings) {
  }

  /**
   * Loads a project definition from the import settings and the .bazelproject file.
   */
  ProjectToLoadDefinition loadProjectDefinition(ProjectViewSet projectViewSet);

  QuerySyncProject loadProject() throws BuildException;

  /**
   * Returns an {@link ImmutableSet} of rule kinds that query sync or plugin know how to resolve
   * symbols for without building. The rules query sync always builds even if they are part of the
   * project are in {@link com.google.idea.blaze.qsync.BlazeQueryParser#ALWAYS_BUILD_RULE_KINDS}
   */
  public static ImmutableSet<String> getHandledRuleKinds(Project project) {
    ImmutableSet.Builder<String> defaultRules = ImmutableSet.builder();
    for (HandledRulesProvider ep : HandledRulesProvider.EP_NAME.getExtensionList()) {
      defaultRules.addAll(ep.handledRuleKinds(project));
    }
    return defaultRules.build();
  }
}
