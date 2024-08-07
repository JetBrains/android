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
package com.google.idea.blaze.base.run;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.sections.RunConfigurationsSection;
import com.google.idea.blaze.base.projectview.section.sections.TargetSection;
import com.google.idea.blaze.base.run.exporter.RunConfigurationSerializer;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncResult;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.common.util.Transactions;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Imports run configurations specified in the project view, and creates run configurations for
 * project view targets, where appropriate.
 */
public class BlazeRunConfigurationSyncListener implements SyncListener {

  @Override
  public void onSyncComplete(
      Project project,
      BlazeContext context,
      BlazeImportSettings importSettings,
      ProjectViewSet projectViewSet,
      ImmutableSet<Integer> buildIds,
      BlazeProjectData blazeProjectData,
      SyncMode syncMode,
      SyncResult syncResult) {
    updateExistingRunConfigurations(project);
    removeInvalidRunConfigurations(project);
    if (syncMode == SyncMode.STARTUP || syncMode == SyncMode.NO_BUILD) {
      return;
    }

    Set<File> xmlFiles =
        getImportedRunConfigurations(projectViewSet, blazeProjectData.getWorkspacePathResolver());
    Transactions.submitTransactionAndWait(
        () -> {
          // First, import from specified XML files. Then auto-generate from targets.
          xmlFiles.forEach(
              (file) -> RunConfigurationSerializer.loadFromXmlIgnoreExisting(project, file));

          Set<Label> labelsWithConfigs = labelsWithConfigs(project);
          Set<TargetExpression> targetExpressions =
              Sets.newLinkedHashSet(projectViewSet.listItems(TargetSection.KEY));
          // We only auto-generate configurations for rules listed in the project view.
          for (TargetExpression target : targetExpressions) {
            if (!(target instanceof Label) || labelsWithConfigs.contains(target)) {
              continue;
            }
            Label label = (Label) target;
            labelsWithConfigs.add(label);
            maybeAddRunConfiguration(project, blazeProjectData, label);
          }
        });
  }

  private static void removeInvalidRunConfigurations(Project project) {
    RunManagerImpl manager = RunManagerImpl.getInstanceImpl(project);
    List<RunnerAndConfigurationSettings> toRemove =
        manager
            .getConfigurationSettingsList(BlazeCommandRunConfigurationType.getInstance())
            .stream()
            .filter(s -> isInvalidRunConfig(s.getConfiguration()))
            .collect(Collectors.toList());
    if (!toRemove.isEmpty()) {
      manager.removeConfigurations(toRemove);
    }
  }

  private static boolean isInvalidRunConfig(RunConfiguration config) {
    return config instanceof BlazeCommandRunConfiguration
        && ((BlazeCommandRunConfiguration) config).pendingSetupFailed();
  }

  /**
   * On each sync, re-calculate target kind for all existing run configurations, in case the target
   * map has changed since the last sync. Also force-enable our before-run task on all
   * configurations.
   */
  private static void updateExistingRunConfigurations(Project project) {
    RunManagerImpl manager = RunManagerImpl.getInstanceImpl(project);
    boolean beforeRunTasksChanged = false;
    for (RunConfiguration config :
        manager.getConfigurationsList(BlazeCommandRunConfigurationType.getInstance())) {
      if (config instanceof BlazeCommandRunConfiguration) {
        ((BlazeCommandRunConfiguration) config).updateTargetKindAsync(null);
        beforeRunTasksChanged |= enableBlazeBeforeRunTask((BlazeCommandRunConfiguration) config);
      }
    }
    if (beforeRunTasksChanged) {
      manager.fireBeforeRunTasksUpdated();
    }
  }

  private static boolean enableBlazeBeforeRunTask(BlazeCommandRunConfiguration config) {
    @SuppressWarnings("rawtypes")
    List<BeforeRunTask> tasks =
        RunManagerEx.getInstanceEx(config.getProject()).getBeforeRunTasks(config);
    if (tasks.stream().noneMatch(t -> t.getProviderId().equals(BlazeBeforeRunTaskProvider.ID))) {
      return addBlazeBeforeRunTask(config);
    }
    boolean changed = false;
    for (BeforeRunTask<?> task : tasks) {
      if (task.getProviderId().equals(BlazeBeforeRunTaskProvider.ID) && !task.isEnabled()) {
        changed = true;
        task.setEnabled(true);
      }
    }
    return changed;
  }

  private static boolean addBlazeBeforeRunTask(BlazeCommandRunConfiguration config) {
    BeforeRunTaskProvider<?> provider =
        BlazeBeforeRunTaskProvider.getProvider(config.getProject(), BlazeBeforeRunTaskProvider.ID);
    if (provider == null) {
      return false;
    }
    BeforeRunTask<?> task = provider.createTask(config);
    if (task == null) {
      return false;
    }
    task.setEnabled(true);

    List<BeforeRunTask<?>> beforeRunTasks = new ArrayList<>(config.getBeforeRunTasks());
    beforeRunTasks.add(task);
    config.setBeforeRunTasks(beforeRunTasks);

    return true;
  }

  private static Set<File> getImportedRunConfigurations(
      ProjectViewSet projectViewSet, WorkspacePathResolver pathResolver) {
    return projectViewSet.listItems(RunConfigurationsSection.KEY).stream()
        .map(pathResolver::resolveToFile)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /** Collects a set of all the Blaze labels that have an associated run configuration. */
  private static Set<Label> labelsWithConfigs(Project project) {
    List<RunConfiguration> configurations =
        RunManager.getInstance(project).getAllConfigurationsList();
    Set<Label> labelsWithConfigs = Sets.newHashSet();
    for (RunConfiguration configuration : configurations) {
      if (configuration instanceof BlazeRunConfiguration) {
        BlazeRunConfiguration config = (BlazeRunConfiguration) configuration;
        config.getTargets().stream()
            .filter(t -> t instanceof Label)
            .map(t -> (Label) t)
            .forEach(labelsWithConfigs::add);
      }
    }
    return labelsWithConfigs;
  }

  /**
   * Adds a run configuration for an android_binary target if there is not already a configuration
   * for that target.
   */
  private static void maybeAddRunConfiguration(
      Project project, BlazeProjectData blazeProjectData, Label label) {
    RunManager runManager = RunManager.getInstance(project);

    for (BlazeRunConfigurationFactory configurationFactory :
        BlazeRunConfigurationFactory.EP_NAME.getExtensions()) {
      if (configurationFactory.handlesTarget(project, blazeProjectData, label)) {
        final RunnerAndConfigurationSettings settings =
            configurationFactory.createForTarget(project, runManager, label);
        runManager.addConfiguration(settings, /* isShared= */ false);
        if (runManager.getSelectedConfiguration() == null) {
          runManager.setSelectedConfiguration(settings);
        }
        break;
      }
    }
  }
}
