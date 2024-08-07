/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.actions;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.query.BlazeQueryLabelKindParser;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.scopes.IdeaLogScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.BlazeSyncManager;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.common.PrintOutput;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

/**
 * Updates the target map, removing any stale targets without requiring an incremental sync.
 *
 * <p>Currently just removes deleted targets.
 */
public class CleanProjectTargetsSyncAction extends BlazeProjectSyncAction {

  @Override
  protected void runSync(Project project, AnActionEvent e) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return;
    }
    // TODO(brendandouglas): move this step under a progress dialog, as part of the core sync
    // process
    @SuppressWarnings("unused") // go/futurereturn-lsc
    Future<?> possiblyIgnoredError =
        PooledThreadExecutor.INSTANCE.submit(
            () ->
                Scope.root(
                    context -> {
                      context.push(new IdeaLogScope());

                      ImmutableSet<Label> deleted =
                          findDeletedTargets(
                              project, projectData.getTargetMap().targets().asList(), context);
                      if (deleted == null) {
                        return;
                      }
                      BlazeSyncManager.getInstance(project)
                          .filterProjectTargets(
                              targetKey -> !deleted.contains(targetKey.getLabel()),
                              /* reason= */ "CleanProjectTargetsSyncAction");
                      removeInvalidRunConfigurations(project, deleted);
                    }));
  }

  /** Max number of targets per query. */
  private static final int SHARD_SIZE = 1000;

  /**
   * Returns the set of targets in the provided list which no longer exist, or null if a query
   * failed.
   */
  @Nullable
  private static ImmutableSet<Label> findDeletedTargets(
      Project project, List<TargetIdeInfo> targets, BlazeContext context) {
    Set<Label> foundTargets = new HashSet<>();
    for (List<TargetIdeInfo> group : Lists.partition(targets, SHARD_SIZE)) {
      ImmutableList<TargetInfo> toKeep = runBlazeQuery(project, getQuery(group), context);
      if (toKeep == null) {
        return null;
      }
      toKeep.forEach(t -> foundTargets.add(t.label));
    }
    return targets.stream()
        .map(t -> t.getKey().getLabel())
        .filter(l -> !foundTargets.contains(l))
        .collect(toImmutableSet());
  }

  private static String getQuery(List<TargetIdeInfo> targets) {
    return targets.stream()
        .map(t -> String.format("'%s'", t.getKey().getLabel()))
        .collect(joining("+"));
  }

  /**
   * Runs a blaze query synchronously, returning an output list of {@link TargetInfo}, or null if
   * the query failed.
   */
  @Nullable
  private static ImmutableList<TargetInfo> runBlazeQuery(
      Project project, String query, BlazeContext context) {
    BlazeCommand command =
        BlazeCommand.builder(getBlazeBinaryPath(project), BlazeCommandName.QUERY)
            .addBlazeFlags("--output=label_kind")
            .addBlazeFlags("--keep_going")
            .addBlazeFlags(query)
            .build();

    BlazeQueryLabelKindParser outputProcessor = new BlazeQueryLabelKindParser(t -> true);
    int retVal =
        ExternalTask.builder(WorkspaceRoot.fromProject(project))
            .addBlazeCommand(command)
            .context(context)
            .stdout(LineProcessingOutputStream.of(outputProcessor))
            .stderr(
                LineProcessingOutputStream.of(
                    line -> {
                      context.output(PrintOutput.error(line));
                      return true;
                    }))
            .build()
            .run();
    if (retVal != 0 && retVal != 3) {
      // A return value of 3 indicates that the query completed, but there were some
      // errors in the query, like querying a directory with no build files / no targets.
      // Instead of returning null, we allow returning the parsed targets, if any.
      return null;
    }
    return outputProcessor.getTargets();
  }

  private static String getBlazeBinaryPath(Project project) {
    BuildSystemProvider buildSystemProvider = Blaze.getBuildSystemProvider(project);
    return buildSystemProvider.getSyncBinaryPath(project);
  }

  private static void removeInvalidRunConfigurations(Project project, ImmutableSet<Label> deleted) {
    RunManagerImpl manager = RunManagerImpl.getInstanceImpl(project);
    List<RunnerAndConfigurationSettings> toRemove =
        manager
            .getConfigurationSettingsList(BlazeCommandRunConfigurationType.getInstance())
            .stream()
            .filter(s -> isDeletedTarget(s.getConfiguration(), deleted))
            .collect(Collectors.toList());
    if (!toRemove.isEmpty()) {
      manager.removeConfigurations(toRemove);
    }
  }

  private static boolean isDeletedTarget(
      RunConfiguration configuration, ImmutableSet<Label> deleted) {
    if (!(configuration instanceof BlazeCommandRunConfiguration)) {
      return false;
    }
    BlazeCommandRunConfiguration config = (BlazeCommandRunConfiguration) configuration;
    ImmutableList<? extends TargetExpression> targets = config.getTargets();
    if (targets.isEmpty()) {
      return false;
    }
    if (deleted.containsAll(targets)) {
      return true;
    }
    ImmutableList<TargetExpression> updated =
        targets.stream().filter(deleted::contains).collect(toImmutableList());
    if (updated.size() == targets.size()) {
      return false;
    }
    config.setTargets(updated);
    return false;
  }

  @Override
  protected QuerySyncStatus querySyncSupport() {
    return QuerySyncStatus.HIDDEN;
  }
}
