/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.dependencies.DirectoryToTargetProvider;
import com.google.idea.blaze.base.dependencies.SourceToTargetFilteringStrategy;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.sections.AutomaticallyDeriveTargetsSection;
import com.google.idea.blaze.base.projectview.section.sections.TargetSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.SyncScope.SyncCanceledException;
import com.google.idea.blaze.base.sync.SyncScope.SyncFailedException;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.common.PrintOutput;
import com.intellij.openapi.project.Project;
import java.util.List;
import java.util.Locale;

/** Derives sync targets from the project directories. */
public final class SyncProjectTargetsHelper {

  private SyncProjectTargetsHelper() {}

  /** The full set of project targets which should be built during sync. */
  public static class ProjectTargets {
    final ImmutableList<TargetExpression> derivedTargets;
    final ImmutableList<TargetExpression> explicitTargets;

    private ProjectTargets(
        ImmutableList<TargetExpression> derivedTargets,
        ImmutableList<TargetExpression> explicitTargets) {
      this.derivedTargets = derivedTargets;
      this.explicitTargets = explicitTargets;
    }

    public ImmutableList<TargetExpression> getTargetsToSync() {
      // add explicit targets after derived targets so users can override automatic behavior
      return ImmutableList.<TargetExpression>builder()
          .addAll(derivedTargets)
          .addAll(explicitTargets)
          .build();
    }
  }

  public static ProjectTargets getProjectTargets(
      Project project,
      BlazeContext context,
      ProjectViewSet viewSet,
      WorkspacePathResolver pathResolver,
      WorkspaceLanguageSettings languageSettings)
      throws SyncFailedException, SyncCanceledException {
    ImmutableList<TargetExpression> derived =
        shouldDeriveSyncTargetsFromDirectories(viewSet)
            ? deriveTargetsFromDirectories(
                project, context, viewSet, pathResolver, languageSettings)
            : ImmutableList.of();
    List<TargetExpression> projectViewTargets = viewSet.listItems(TargetSection.KEY);
    return new ProjectTargets(derived, ImmutableList.copyOf(projectViewTargets));
  }

  private static boolean shouldDeriveSyncTargetsFromDirectories(ProjectViewSet viewSet) {
    return viewSet.getScalarValue(AutomaticallyDeriveTargetsSection.KEY).orElse(false);
  }

  private static ImmutableList<TargetExpression> deriveTargetsFromDirectories(
      Project project,
      BlazeContext context,
      ProjectViewSet projectViewSet,
      WorkspacePathResolver pathResolver,
      WorkspaceLanguageSettings languageSettings)
      throws SyncFailedException, SyncCanceledException {
    String fileBugSuggestion =
        Blaze.getBuildSystemName(project) == BuildSystemName.Bazel
            ? ""
            : " Please run 'Help > File a Bug'";
    if (!DirectoryToTargetProvider.hasProvider()) {
      IssueOutput.error(
              "Can't derive targets from project directories: no query provider available."
                  + fileBugSuggestion)
          .submit(context);
      throw new SyncFailedException();
    }
    ImportRoots importRoots = ImportRoots.builder(project).add(projectViewSet).build();
    if (importRoots.rootDirectories().isEmpty()) {
      return ImmutableList.of();
    }
    List<TargetInfo> targets =
        Scope.push(
            context,
            childContext -> {
              childContext.push(
                  new TimingScope("QueryDirectoryTargets", EventType.BlazeInvocation));
              childContext.output(new StatusOutput("Querying targets in project directories..."));
              // We don't want blaze build errors to fail the whole sync
              childContext.setPropagatesErrors(false);
              return DirectoryToTargetProvider.expandDirectoryTargets(
                  project, importRoots, pathResolver, childContext);
            });
    if (context.isCancelled()) {
      throw new SyncCanceledException();
    }
    if (targets == null) {
      IssueOutput.error("Deriving targets from project directories failed." + fileBugSuggestion)
          .submit(context);
      throw new SyncFailedException();
    }
    ImmutableList<TargetExpression> retained =
        SourceToTargetFilteringStrategy.filterTargets(targets).stream()
            .filter(
                t ->
                    t.getKind() != null
                        && t.getKind().getLanguageClasses().stream()
                            .anyMatch(languageSettings::isLanguageActive))
            .map(t -> t.label)
            .collect(toImmutableList());
    context.output(
        PrintOutput.log(
            String.format(Locale.ROOT,
                          "%d targets found under project directories; syncing %d of them.",
                          targets.size(), retained.size())));
    return retained;
  }
}
