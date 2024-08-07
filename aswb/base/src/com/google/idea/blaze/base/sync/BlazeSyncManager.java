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

import static java.util.stream.Collectors.joining;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.idea.blaze.base.async.executor.ProgressiveTaskWithProgressIndicator;
import com.google.idea.blaze.base.command.BlazeInvocationContext.ContextType;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.issueparser.BlazeIssueParser;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.qsync.NotSupportedWithQuerySyncException;
import com.google.idea.blaze.base.qsync.QuerySyncPromo;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.BlazeScope;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.SummaryOutput;
import com.google.idea.blaze.base.scope.output.SummaryOutput.Prefix;
import com.google.idea.blaze.base.scope.scopes.ProblemsViewScope;
import com.google.idea.blaze.base.scope.scopes.ProgressIndicatorScope;
import com.google.idea.blaze.base.scope.scopes.ToolWindowScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.settings.BlazeUserSettings.FocusBehavior;
import com.google.idea.blaze.base.sync.SyncScope.SyncCanceledException;
import com.google.idea.blaze.base.sync.SyncScope.SyncFailedException;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.projectview.SyncDirectoriesWarning;
import com.google.idea.blaze.base.sync.status.BlazeSyncStatus;
import com.google.idea.blaze.base.toolwindow.Task;
import com.google.idea.blaze.base.util.SaveUtil;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.common.PrintOutput.OutputType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.text.StringUtil;
import java.util.Collection;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import org.jetbrains.annotations.VisibleForTesting;

/** Manages syncing and its listeners. */
public class BlazeSyncManager {

  private final Project project;
  private static final Logger logger = Logger.getInstance(BlazeSyncManager.class);

  public BlazeSyncManager(Project project) {
    this.project = project;
  }

  public static BlazeSyncManager getInstance(Project project) {
    return project.getService(BlazeSyncManager.class);
  }

  public static void printAndLogError(String errorMessage, Context<?> context) {
    context.output(PrintOutput.error(errorMessage));
    logger.error(errorMessage);
  }

  /** Requests a project sync with Blaze. */
  public void requestProjectSync(BlazeSyncParams syncParams) {
    if (Blaze.getProjectType(project) == ProjectType.QUERY_SYNC) {
      throw new NotSupportedWithQuerySyncException("legacy sync requested");
    }
    if (syncParams.syncMode() == SyncMode.NO_BUILD
        && !syncParams.backgroundSync()
        && !SyncDirectoriesWarning.warn(project)) {
      return;
    }
    SaveUtil.saveAllFiles();

    BlazeImportSettings importSettings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();
    if (importSettings == null) {
      throw new IllegalStateException(
          String.format("Attempt to sync non-%s project.", Blaze.buildSystemName(project)));
    }

    // an additional call to 'sync started'. This disables the sync actions while we wait for
    // 'runWhenSmart'
    BlazeSyncStatus.getInstance(project).syncStarted();
    DumbService.getInstance(project)
        .runWhenSmart(
            () -> {
              Future<Void> unusedFuture =
                  ProgressiveTaskWithProgressIndicator.builder(project, "Initiating project sync")
                      .submitTask(
                          indicator ->
                              Scope.root(
                                  context -> {
                                    context
                                        .push(new ProgressIndicatorScope(indicator))
                                        .push(buildToolWindowScope(syncParams, indicator))
                                        .push(
                                            new ProblemsViewScope(
                                                project,
                                                BlazeUserSettings.getInstance()
                                                    .getShowProblemsViewOnSync()));
                                    if (!runInitialDirectoryOnlySync(syncParams)) {
                                      executeTask(project, syncParams, context);
                                      return;
                                    }
                                    // Call shouldForceFullSync before initial directory update
                                    // because it relies on old project data which is erased during
                                    // initial directory update
                                    BlazeProjectData oldProjectData =
                                        BlazeProjectDataManager.getInstance(project)
                                            .getBlazeProjectData();
                                    SyncProjectState projectState = null;
                                    try {
                                      projectState =
                                          ProjectStateSyncTask.collectProjectState(
                                              project, context);
                                    } catch (SyncCanceledException | SyncFailedException e) {
                                      ApplicationManager.getApplication()
                                          .invokeLater(
                                              () ->
                                                  BlazeSyncStatus.getInstance(project)
                                                      .syncEnded(
                                                          syncParams.syncMode(),
                                                          context.getSyncResult()));
                                      throw new VerifyException(e);
                                    }
                                    boolean forceFullSync =
                                        shouldForceFullSync(
                                            oldProjectData,
                                            projectState,
                                            syncParams.syncMode(),
                                            context);
                                    BlazeSyncParams initialUpdateSyncParams =
                                        BlazeSyncParams.builder()
                                            .setTitle("Initial directory update")
                                            .setSyncMode(SyncMode.NO_BUILD)
                                            .setSyncOrigin(syncParams.syncOrigin())
                                            .setBackgroundSync(true)
                                            .build();
                                    executeTask(project, initialUpdateSyncParams, context);

                                    BlazeSyncParams updatedSyncParams =
                                        forceFullSync
                                            ? syncParams.toBuilder()
                                                .setSyncMode(SyncMode.FULL)
                                                .setTitle("Full Sync")
                                                .build()
                                            : syncParams;
                                    if (!context.isCancelled()) {
                                      executeTask(project, updatedSyncParams, context);
                                    }
                                  }));
            });
  }

  @VisibleForTesting
  boolean shouldForceFullSync(
      BlazeProjectData oldProjectData,
      SyncProjectState projectState,
      SyncMode syncMode,
      BlazeContext context) {
    if (oldProjectData == null || projectState == null || syncMode == SyncMode.NO_BUILD) {
      return false;
    }
    SetView<LanguageClass> newLanguages =
        Sets.difference(
            projectState.getLanguageSettings().getActiveLanguages(),
            oldProjectData.getWorkspaceLanguageSettings().getActiveLanguages());
    // Don't care if languages are removed from project view because the corresponding targets will
    // be removed from the targetMap anyway at the end of the sync
    if (newLanguages.isEmpty()) {
      return false;
    }
    // Force a full sync if a new language is added to project view
    String message =
        String.format(
            "%s %s added to project view; forcing a full sync",
            StringUtil.pluralize("Language", newLanguages.size()),
            newLanguages.stream().map(LanguageClass::getName).collect(joining(",")));
    context.output(SummaryOutput.output(Prefix.INFO, message).log());
    logger.info(message);
    return true;
  }

  private static void executeTask(Project project, BlazeSyncParams params, BlazeContext context) {
    Future<?> querySyncPromoFuture = new QuerySyncPromo(project).getPromoShowFuture();
    try {
      SyncPhaseCoordinator.getInstance(project).syncProject(params, context).get();
    } catch (InterruptedException e) {
      context.output(new PrintOutput("Sync interrupted: " + e.getMessage()));
      context.setCancelled();
    } catch (ExecutionException e) {
      context.output(new PrintOutput(e.getMessage(), OutputType.ERROR));
      context.setHasError();
    } catch (CancellationException e) {
      context.output(new PrintOutput("Sync cancelled"));
      context.setCancelled();
    } finally {
      querySyncPromoFuture.cancel(false);
    }
  }

  private Task getRootInvocationTask(BlazeSyncParams params) {
    String taskTitle;
    if (params.syncMode() == SyncMode.STARTUP) {
      taskTitle = "Startup Sync";
    } else if (params.syncOrigin().equals(BlazeSyncStartupActivity.SYNC_REASON)) {
      taskTitle = "Importing " + project.getName();
    } else if (params.syncMode() == SyncMode.PARTIAL) {
      taskTitle = "Partial Sync";
    } else if (params.syncMode() == SyncMode.FULL) {
      taskTitle = "Non-Incremental Sync";
    } else {
      taskTitle = "Incremental Sync";
    }
    return new Task(project, taskTitle, Task.Type.SYNC);
  }

  private BlazeScope buildToolWindowScope(BlazeSyncParams syncParams, ProgressIndicator indicator) {
    BlazeUserSettings userSettings = BlazeUserSettings.getInstance();
    return new ToolWindowScope.Builder(project, getRootInvocationTask(syncParams))
        .setProgressIndicator(indicator)
        .setPopupBehavior(
            syncParams.backgroundSync()
                ? FocusBehavior.NEVER
                : userSettings.getShowBlazeConsoleOnSync())
        .setIssueParsers(
            BlazeIssueParser.defaultIssueParsers(
                project, WorkspaceRoot.fromProject(project), ContextType.Sync))
        .showSummaryOutput()
        .build();
  }

  private static boolean runInitialDirectoryOnlySync(BlazeSyncParams syncParams) {
    switch (syncParams.syncMode()) {
      case NO_BUILD:
      case STARTUP:
        return false;
      case FULL:
        return true;
      case INCREMENTAL:
      case PARTIAL:
        return !syncParams.backgroundSync();
    }
    throw new AssertionError("Unhandled syncMode: " + syncParams.syncMode());
  }

  /**
   * Runs a non-incremental full project sync, clearing the previous project data.
   *
   * @param reason a description of what triggered this sync
   */
  public void fullProjectSync(String reason) {
    BlazeSyncParams syncParams =
        BlazeSyncParams.builder()
            .setTitle("Full Sync")
            .setSyncMode(SyncMode.FULL)
            .setSyncOrigin(reason)
            .setAddProjectViewTargets(true)
            .setAddWorkingSet(BlazeUserSettings.getInstance().getExpandSyncToWorkingSet())
            .build();
    requestProjectSync(syncParams);
  }

  /**
   * Syncs the entire project.
   *
   * @param reason a description of what triggered this sync
   */
  public void incrementalProjectSync(String reason) {
    BlazeSyncParams syncParams =
        BlazeSyncParams.builder()
            .setTitle("Sync")
            .setSyncMode(SyncMode.INCREMENTAL)
            .setSyncOrigin(reason)
            .setAddProjectViewTargets(true)
            .setAddWorkingSet(BlazeUserSettings.getInstance().getExpandSyncToWorkingSet())
            .build();
    requestProjectSync(syncParams);
  }

  /**
   * Runs a partial sync of the given targets.
   *
   * @param reason a description of what triggered this sync
   */
  public void partialSync(Collection<? extends TargetExpression> targetExpressions, String reason) {
    partialSync(targetExpressions, ImmutableList.of(), reason);
  }

  /**
   * Runs a partial sync of the given targets and source files. During sync, a query will be run to
   * convert the source files to the targets building them.
   *
   * @param reason a description of what triggered this sync
   */
  public void partialSync(
      Collection<? extends TargetExpression> targetExpressions,
      Collection<WorkspacePath> sources,
      String reason) {
    BlazeSyncParams syncParams =
        BlazeSyncParams.builder()
            .setTitle("Partial Sync")
            .setSyncMode(SyncMode.PARTIAL)
            .setSyncOrigin(reason)
            .addTargetExpressions(targetExpressions)
            .addSourceFilesToSync(sources)
            .build();
    requestProjectSync(syncParams);
  }

  /**
   * Filters the project targets as part of a coherent sync process, updating derived project data
   * and sending notifications accordingly.
   *
   * @param reason a description of what triggered this action
   */
  public void filterProjectTargets(Predicate<TargetKey> filter, String reason) {
    StartupManager.getInstance(project)
        .runWhenProjectIsInitialized(
            () -> SyncPhaseCoordinator.getInstance(project).filterProjectTargets(filter, reason));
  }

  /**
   * Runs a directory-only sync, without any 'blaze build' operations.
   *
   * @param inBackground run in the background, suppressing the normal 'no targets will be build'
   *     warning.
   * @param reason a description of what triggered this sync
   */
  public void directoryUpdate(boolean inBackground, String reason) {
    BlazeSyncParams syncParams =
        BlazeSyncParams.builder()
            .setTitle("Update Directories")
            .setSyncMode(SyncMode.NO_BUILD)
            .setSyncOrigin(reason)
            .setBackgroundSync(inBackground)
            .build();
    requestProjectSync(syncParams);
  }

  /**
   * Runs a sync of the 'working set' (the locally modified files).
   *
   * @param reason a description of what triggered this sync
   */
  public void workingSetSync(String reason) {
    BlazeSyncParams syncParams =
        BlazeSyncParams.builder()
            .setTitle("Sync Working Set")
            .setSyncMode(SyncMode.PARTIAL)
            .setSyncOrigin(reason)
            .setAddWorkingSet(true)
            .build();
    requestProjectSync(syncParams);
  }
}
