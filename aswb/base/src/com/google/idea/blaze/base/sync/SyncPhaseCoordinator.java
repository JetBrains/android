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

import static java.util.stream.Collectors.joining;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.base.async.executor.ProgressiveTaskWithProgressIndicator;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.bazel.BuildSystem.SyncStrategy;
import com.google.idea.blaze.base.command.BlazeInvocationContext.ContextType;
import com.google.idea.blaze.base.experiments.ExperimentScope;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.issueparser.BlazeIssueParser;
import com.google.idea.blaze.base.logging.EventLoggingService;
import com.google.idea.blaze.base.logging.utils.BuildPhaseSyncStats;
import com.google.idea.blaze.base.logging.utils.SyncStats;
import com.google.idea.blaze.base.model.AspectSyncProjectData;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.ProjectTargetData;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.sections.ImportSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.scope.output.SummaryOutput;
import com.google.idea.blaze.base.scope.output.SummaryOutput.Prefix;
import com.google.idea.blaze.base.scope.scopes.IdeaLogScope;
import com.google.idea.blaze.base.scope.scopes.NetworkTrafficTrackingScope;
import com.google.idea.blaze.base.scope.scopes.NotificationScope;
import com.google.idea.blaze.base.scope.scopes.PerformanceWarningScope;
import com.google.idea.blaze.base.scope.scopes.ProblemsViewScope;
import com.google.idea.blaze.base.scope.scopes.ProgressIndicatorScope;
import com.google.idea.blaze.base.scope.scopes.SharedStringPoolScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.scope.scopes.TimingScopeListener.TimedEvent;
import com.google.idea.blaze.base.scope.scopes.ToolWindowScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.settings.BlazeUserSettings.FocusBehavior;
import com.google.idea.blaze.base.settings.BuildBinaryType;
import com.google.idea.blaze.base.sync.SyncScope.SyncCanceledException;
import com.google.idea.blaze.base.sync.SyncScope.SyncFailedException;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.google.idea.blaze.base.sync.aspects.BuildResult.Status;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.libraries.BlazeLibraryCollector;
import com.google.idea.blaze.base.sync.projectstructure.ModuleFinder;
import com.google.idea.blaze.base.toolwindow.Task;
import com.google.idea.blaze.base.util.SaveUtil;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.common.util.ConcurrencyUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/** Manages sync execution, coordinating the possibly-separate build/update phases. */
final class SyncPhaseCoordinator {

  private static final Logger logger = Logger.getInstance(SyncPhaseCoordinator.class);

  static SyncPhaseCoordinator getInstance(Project project) {
    return project.getService(SyncPhaseCoordinator.class);
  }

  private enum SyncPhase {
    BUILD, // just the build phase
    PROJECT_UPDATE, // just the project update phase
    ALL_PHASES, // running all sync phases synchronously
  }

  @AutoValue
  abstract static class UpdatePhaseTask {
    abstract Instant startTime();

    abstract BlazeSyncParams syncParams();

    @Nullable
    abstract SyncProjectState projectState();

    abstract ImmutableSet<Integer> buildIds();

    abstract BlazeSyncBuildResult buildResult();

    abstract SyncResult syncResult();

    abstract BuildBinaryType buildBinaryType();

    abstract Builder toBuilder();

    static Builder builder() {
      return new AutoValue_SyncPhaseCoordinator_UpdatePhaseTask.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {

      abstract Builder setStartTime(Instant value);

      abstract Builder setSyncParams(BlazeSyncParams value);

      abstract Builder setProjectState(SyncProjectState value);

      abstract Builder setBuildIds(ImmutableSet<Integer> value);

      abstract Builder setBuildResult(BlazeSyncBuildResult value);

      abstract Builder setSyncResult(SyncResult value);

      abstract Builder setBuildBinaryType(BuildBinaryType value);

      abstract UpdatePhaseTask build();
    }

    /** Combines this task with another task (relative input ordering is unimportant). */
    static UpdatePhaseTask combineTasks(UpdatePhaseTask a, UpdatePhaseTask b) {
      ImmutableSet<Integer> buildIds =
          ImmutableSet.<Integer>builder().addAll(a.buildIds()).addAll(b.buildIds()).build();
      UpdatePhaseTask first = a.startTime().isBefore(b.startTime()) ? a : b;
      UpdatePhaseTask second = a.startTime().isBefore(b.startTime()) ? b : a;
      // if one of the builds failed entirely, ignore the build result
      if (!first.syncResult().successful()) {
        return second.toBuilder().setStartTime(first.startTime()).setBuildIds(buildIds).build();
      }
      if (!second.syncResult().successful()) {
        return first.toBuilder().setBuildIds(buildIds).build();
      }
      // take the most recent version of the project data, and combine the blaze build outputs
      return builder()
          .setStartTime(first.startTime())
          .setSyncParams(BlazeSyncParams.combine(first.syncParams(), second.syncParams()))
          .setProjectState(second.projectState())
          .setBuildIds(buildIds)
          .setBuildResult(first.buildResult().updateResult(second.buildResult()))
          .setSyncResult(SyncResult.combine(first.syncResult(), second.syncResult()))
          .build();
    }
  }

  // an application-wide cap on the number of concurrent remote builds
  private static final int MAX_BUILD_TASKS = 8;

  // an application-wide executor to run concurrent blaze builds remotely
  private static final ListeningExecutorService remoteBuildExecutor =
      MoreExecutors.listeningDecorator(
          AppExecutorUtil.createBoundedApplicationPoolExecutor("FetchExecutor", MAX_BUILD_TASKS));

  // a per-project executor to run single-threaded sync phases
  private final ListeningExecutorService singleThreadedExecutor;
  private final Project project;
  private final BuildSystem buildSystem;

  /**
   * An integer uniquely identifying each build task. Used to track in-progress syncs on a
   * per-target basis.
   */
  private static final AtomicInteger nextBuildId = new AtomicInteger();

  @Nullable
  @GuardedBy("this")
  private UpdatePhaseTask pendingUpdateTask;

  SyncPhaseCoordinator(Project project) {
    this.project = project;
    singleThreadedExecutor =
        MoreExecutors.listeningDecorator(
            Executors.newSingleThreadExecutor(
                ConcurrencyUtil.namedDaemonThreadPoolFactory(BlazeSyncManager.class)));
    buildSystem = Blaze.getBuildSystemProvider(project).getBuildSystem();
  }

  private boolean useRemoteExecutor(BlazeSyncParams syncParams) {
    if (syncParams.syncMode() == SyncMode.NO_BUILD) {
      return false;
    }
    SyncStrategy strategy = buildSystem.getSyncStrategy(project);
    switch (strategy) {
      case DECIDE_AUTOMATICALLY:
      case PARALLEL:
        return true;
      case SERIAL:
        return false;
    }
    throw new IllegalStateException("Invalid sync strategy: " + strategy);
  }

  ListenableFuture<Void> syncProject(BlazeSyncParams syncParams, BlazeContext parentContext) {
    boolean singleThreaded = !useRemoteExecutor(syncParams);
    return ProgressiveTaskWithProgressIndicator.builder(project, "Syncing Project")
        .setExecutor(singleThreaded ? singleThreadedExecutor : remoteBuildExecutor)
        .submitTask(
            indicator ->
                Scope.push(
                    parentContext,
                    context -> {
                      ToolWindowScope parentToolWindowScope =
                          parentContext.getScope(ToolWindowScope.class);
                      Task parentToolWindowTask =
                          parentToolWindowScope != null ? parentToolWindowScope.getTask() : null;

                      BlazeSyncParams params = finalizeSyncParams(syncParams, context);
                      Task syncTask =
                          new Task(project, params.title(), Task.Type.SYNC, parentToolWindowTask);
                      setupScopes(
                          params,
                          context,
                          indicator,
                          singleThreaded ? SyncPhase.ALL_PHASES : SyncPhase.BUILD,
                          syncTask);
                      parentContext.output(
                          SummaryOutput.output(
                              Prefix.TIMESTAMP, String.format("%s started", syncTask.getName())));
                      runSync(params, singleThreaded, context);
                      if (context.hasErrors()) {
                        parentContext.output(
                            SummaryOutput.error(
                                Prefix.TIMESTAMP,
                                String.format("%s finished with errors", syncTask.getName())));
                      } else {
                        parentContext.output(
                            SummaryOutput.output(
                                Prefix.TIMESTAMP,
                                String.format("%s finished", syncTask.getName())));
                      }
                    }));
  }

  /**
   * Filters the project targets as part of a coherent sync process, updating derived project data
   * and sending notifications accordingly.
   *
   * @param reason a description of what triggered this action
   */
  void filterProjectTargets(Predicate<TargetKey> filter, String reason) {
    @SuppressWarnings("unused") // go/futurereturn-lsc
    Future<?> possiblyIgnoredError =
        ProgressiveTaskWithProgressIndicator.builder(project, "Filtering Project Targets")
            .setExecutor(singleThreadedExecutor)
            .submitTask(
                indicator ->
                    Scope.root(
                        context -> {
                          BlazeSyncParams syncParams =
                              BlazeSyncParams.builder()
                                  .setTitle("Filtering targets")
                                  .setSyncMode(SyncMode.PARTIAL)
                                  .setSyncOrigin(reason)
                                  .setBackgroundSync(true)
                                  .build();
                          BlazeSyncParams params = finalizeSyncParams(syncParams, context);
                          setupScopes(
                              params,
                              context,
                              indicator,
                              SyncPhase.ALL_PHASES,
                              new Task(project, params.title(), Task.Type.SYNC));
                          context.output(
                              SummaryOutput.output(
                                  Prefix.TIMESTAMP, String.format("%s started", params.title())));
                          doFilterProjectTargets(params, filter, context);
                        }));
  }

  private BlazeSyncParams finalizeSyncParams(BlazeSyncParams params, BlazeContext context) {
    BlazeProjectData oldProjectData = getOldProjectData(context, params.syncMode());
    if (oldProjectData == null && params.syncMode() != SyncMode.NO_BUILD) {
      params = params.toBuilder().setSyncMode(SyncMode.FULL).setAddProjectViewTargets(true).build();
    }
    return params;
  }

  @Nullable
  private AspectSyncProjectData getOldProjectData(BlazeContext context, SyncMode mode) {
    if (mode == SyncMode.FULL) {
      return null;
    }
    BlazeImportSettings importSettings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();
    Preconditions.checkState(
        Blaze.getProjectType(project) == ProjectType.ASPECT_SYNC,
        "This should only happen in legacy sync");

    BlazeProjectData data =
        BlazeProjectDataManager.getInstance(project).loadProject(importSettings);
    if (data == null && mode != SyncMode.NO_BUILD) {
      context.output(
          new StatusOutput(
              "Couldn't load previously cached project data; full sync will be needed"));
    }
    if (data == null) {
      return null;
    }
    Preconditions.checkState(data instanceof AspectSyncProjectData, "Invalid project data type");
    return (AspectSyncProjectData) data;
  }

  private void doFilterProjectTargets(
      BlazeSyncParams params, Predicate<TargetKey> filter, BlazeContext context) {
    Instant startTime = Instant.now();
    SyncResult syncResult = SyncResult.FAILURE;
    SyncStats.Builder stats = SyncStats.builder();
    try {
      SaveUtil.saveAllFiles();
      onSyncStart(project, context, params.syncMode());
      if (!context.shouldContinue()) {
        return;
      }
      AspectSyncProjectData oldProjectData = getOldProjectData(context, params.syncMode());
      if (oldProjectData == null) {
        String message = "Can't filter project targets: project has never been synced.";
        context.output(PrintOutput.error(message));
        IssueOutput.warn(message).submit(context);
        return;
      }

      List<TimedEvent> timedEvents =
          SyncScope.runWithTiming(
              context,
              childContext -> {
                SyncProjectState projectState =
                    ProjectStateSyncTask.collectProjectState(project, context);
                if (projectState == null) {
                  return;
                }
                ProjectTargetData targetData =
                    oldProjectData
                        .getTargetData()
                        .filter(filter, projectState.getLanguageSettings());

                fillInBuildStats(stats, projectState, /* buildResult= */ null);
                ProjectUpdateSyncTask.runProjectUpdatePhase(
                    project,
                    params.syncMode(),
                    projectState,
                    targetData,
                    oldProjectData.getBlazeInfo(),
                    childContext);
              },
              new TimingScope("Filtering project targets", EventType.Other));
      stats.addTimedEvents(timedEvents);
      if (context.shouldContinue()) {
        syncResult = SyncResult.SUCCESS;
      } else {
        syncResult = context.getSyncResult();
      }
    } catch (Throwable e) {
      logSyncError(context, e);
    } finally {
      finishSync(
          params,
          startTime,
          context,
          ProjectViewManager.getInstance(project).getProjectViewSet(),
          ImmutableSet.of(),
          syncResult,
          stats);
    }
  }

  /**
   * Kicks off a sync with the given parameters.
   *
   * @param singleThreaded if true, runs all sync phases synchronously, on the current thread.
   *     Otherwise runs the build phase then passes the result to the project update queue.
   */
  @VisibleForTesting
  void runSync(BlazeSyncParams params, boolean singleThreaded, BlazeContext context) {
    Instant startTime = Instant.now();
    int buildId = nextBuildId.getAndIncrement();
    try {
      SaveUtil.saveAllFiles();
      onSyncStart(project, context, params.syncMode());
      if (!context.shouldContinue()) {
        finishSync(
            params,
            startTime,
            context,
            ProjectViewManager.getInstance(project).getProjectViewSet(),
            ImmutableSet.of(buildId),
            context.getSyncResult(),
            SyncStats.builder());
        return;
      }

      if (params.syncMode() == SyncMode.STARTUP) {
        finishSync(
            params,
            startTime,
            context,
            ProjectViewManager.getInstance(project).getProjectViewSet(),
            ImmutableSet.of(buildId),
            SyncResult.SUCCESS,
            SyncStats.builder());
        return;
      }
      SyncProjectState projectState = ProjectStateSyncTask.collectProjectState(project, context);
      BlazeSyncBuildResult buildResult =
          BuildPhaseSyncTask.runBuildPhase(
              project, params, projectState, buildId, context, buildSystem);

      UpdatePhaseTask task =
          UpdatePhaseTask.builder()
              .setStartTime(startTime)
              .setSyncParams(params)
              .setProjectState(projectState)
              .setBuildIds(ImmutableSet.of(buildId))
              .setBuildResult(buildResult)
              .setSyncResult(syncResultFromBuildPhase(buildResult, context))
              .setBuildBinaryType(
                  buildResult.getBuildPhaseStats().stream()
                      .map(BuildPhaseSyncStats::buildBinaryType)
                      .flatMap(Optional::stream)
                      .findFirst()
                      .orElse(BuildBinaryType.NONE))
              .build();

      if (singleThreaded) {
        updateProjectAndFinishSync(task, context);
      } else {
        queueUpdateTask(task);
      }
    } catch (Throwable e) {
      logSyncError(context, e);
      if (e instanceof SyncCanceledException || e instanceof ProcessCanceledException) {
        context.setCancelled();
      } else {
        context.setHasError();
      }
      finishSync(
          params,
          startTime,
          context,
          ProjectViewManager.getInstance(project).getProjectViewSet(),
          ImmutableSet.of(buildId),
          context.getSyncResult(),
          SyncStats.builder());
    }
  }

  private void queueUpdateTask(UpdatePhaseTask task) {
    synchronized (this) {
      if (pendingUpdateTask != null) {
        // there's already a pending job, no need to kick off another one
        pendingUpdateTask = UpdatePhaseTask.combineTasks(pendingUpdateTask, task);
        return;
      }
      pendingUpdateTask = task;
    }

    ProgressiveTaskWithProgressIndicator.builder(project, "Syncing Project")
        .setExecutor(singleThreadedExecutor)
        .submitTaskLater(
            indicator ->
                Scope.root(
                    context -> {
                      UpdatePhaseTask updateTask = getAndClearPendingTask();
                      setupScopes(
                          updateTask.syncParams(),
                          context,
                          indicator,
                          SyncPhase.PROJECT_UPDATE,
                          new Task(project, "Updating project", Task.Type.SYNC));
                      updateProjectAndFinishSync(updateTask, context);
                    }));
  }

  private synchronized UpdatePhaseTask getAndClearPendingTask() {
    UpdatePhaseTask task = Preconditions.checkNotNull(pendingUpdateTask);
    pendingUpdateTask = null;
    return task;
  }

  private SyncResult syncResultFromBuildPhase(
      BlazeSyncBuildResult syncBuildResult, BlazeContext context) {
    if (context.isCancelled()) {
      return SyncResult.CANCELLED;
    }
    BlazeBuildOutputs buildOutputs = syncBuildResult.getBuildResult();
    if (buildOutputs == null || !syncBuildResult.hasValidOutputs()) {
      return SyncResult.FAILURE;
    }
    if (buildOutputs.buildResult.status == Status.FATAL_ERROR) {
      if (BuildPhaseSyncTask.continueSyncOnOom.getValue()) {
        context.output(
            PrintOutput.error(
                "One or more build shards failed to complete. "
                    + "The project may not be fully updated or resolve for affected targets."));
        return SyncResult.PARTIAL_SUCCESS;
      } else {
        return SyncResult.FAILURE;
      }
    }
    if (buildOutputs.buildResult.status == BuildResult.Status.BUILD_ERROR) {
      String buildSystem = Blaze.buildSystemName(project);
      String message =
          String.format(
              "Sync was successful, but there were %1$s build errors. "
                  + "The project may not be fully updated or resolve until fixed. "
                  + "If the errors are from your working set, please uncheck "
                  + "'%1$s > Sync > Expand Sync to Working Set' and try again.",
              buildSystem);
      context.output(PrintOutput.error(message));
      IssueOutput.warn(message).submit(context);
      return SyncResult.PARTIAL_SUCCESS;
    }
    return SyncResult.SUCCESS;
  }

  private void updateProjectAndFinishSync(UpdatePhaseTask updateTask, BlazeContext context) {
    SyncStats.Builder stats = SyncStats.builder();
    stats.setSyncBinaryType(updateTask.buildBinaryType());
    SyncResult syncResult = updateTask.syncResult();
    try {
      fillInBuildStats(stats, updateTask.projectState(), updateTask.buildResult());
      if (!syncResult.successful() || !updateTask.buildResult().hasValidOutputs()) {
        return;
      }
      List<TimedEvent> timedEvents =
          SyncScope.runWithTiming(
              context,
              childContext -> {
                ProjectTargetData targetData = updateTargetData(updateTask, childContext);
                if (targetData == null) {
                  childContext.setHasError();
                  throw new SyncFailedException();
                }

                ProjectUpdateSyncTask.runProjectUpdatePhase(
                    project,
                    updateTask.syncParams().syncMode(),
                    updateTask.projectState(),
                    targetData,
                    updateTask.buildResult().getBlazeInfo(),
                    childContext);
              },
              new TimingScope("Project update phase", EventType.Other));
      stats.addTimedEvents(timedEvents);
      if (!context.shouldContinue()) {
        syncResult = context.getSyncResult();
      }
    } catch (Throwable e) {
      logSyncError(context, e);
      syncResult = SyncResult.FAILURE;
    } finally {
      SyncProjectState projectState = updateTask.projectState();
      finishSync(
          updateTask.syncParams(),
          updateTask.startTime(),
          context,
          projectState != null ? projectState.getProjectViewSet() : null,
          updateTask.buildIds(),
          syncResult,
          stats);
    }
  }

  @Nullable
  private ProjectTargetData updateTargetData(UpdatePhaseTask task, BlazeContext context) {
    return ProjectUpdateSyncTask.updateTargetData(
        project, task.syncParams(), task.projectState(), task.buildResult(), context);
  }

  /**
   * Completes the sync, updating the in-memory state and handling notifications and logging.
   *
   * <p>This is synchronized because in-memory state updates are not thread-safe, and it can be
   * called during the build or project update phases.
   */
  private synchronized void finishSync(
      BlazeSyncParams syncParams,
      Instant startTime,
      BlazeContext context,
      @Nullable ProjectViewSet projectViewSet,
      ImmutableSet<Integer> buildIds,
      SyncResult syncResult,
      SyncStats.Builder stats) {
    try {
      String syncStatus = "finished";
      if (syncResult.successful()) {
        Preconditions.checkNotNull(projectViewSet);
        BlazeProjectData projectData =
            Preconditions.checkNotNull(
                BlazeProjectDataManager.getInstance(project).getBlazeProjectData());
        if (syncParams.syncMode() != SyncMode.NO_BUILD) {
          stats.addTimedEvents(
              updateInMemoryState(
                  project, context, projectViewSet, projectData, syncParams.syncMode()));
        }
        int librariesCount = BlazeLibraryCollector.getLibraries(projectViewSet, projectData).size();
        stats
            .setTargetMapSize(projectData.getTargetMap().targets().size())
            .setLibraryCount(librariesCount);
        onSyncComplete(
            project, context, projectViewSet, buildIds, projectData, syncParams, syncResult);
      } else {
        syncStatus = SyncResult.CANCELLED.equals(syncResult) ? "canceled" : "failed";
      }
      NetworkTrafficTrackingScope networkTraffic =
          context.getScope(NetworkTrafficTrackingScope.class);
      if (networkTraffic != null) {
        stats.addNetworkUsage(networkTraffic.getNetworkUsage());
      }
      stats
          .setSyncMode(syncParams.syncMode())
          .setSyncTitle(syncParams.title())
          .setSyncOrigin(syncParams.syncOrigin())
          .setSyncResult(syncResult)
          .setStartTime(startTime)
          .setBlazeExecTime(totalBlazeTime(stats.getCurrentTimedEvents()))
          .setTotalClockTime(Duration.between(startTime, Instant.now()));
      EventLoggingService.getInstance().log(stats.build());
      context.output(new StatusOutput("Sync " + syncStatus));
      outputTimingSummary(context, stats.getCurrentTimedEvents());

    } catch (Throwable e) {
      logSyncError(context, e);
    } finally {
      afterSync(project, syncParams, context, syncResult, buildIds);
    }
  }

  /** Sets up the root {@link BlazeContext} for the given {@link SyncPhase}. */
  private void setupScopes(
      BlazeSyncParams syncParams,
      BlazeContext context,
      ProgressIndicator indicator,
      SyncPhase phase,
      Task task) {
    boolean clearProblems = phase != SyncPhase.PROJECT_UPDATE;
    boolean notifyFinished = phase != SyncPhase.BUILD;

    context.push(new ExperimentScope());
    if (BlazeUserSettings.getInstance().getShowPerformanceWarnings()) {
      context.push(new PerformanceWarningScope());
    }
    context.push(new ProgressIndicatorScope(indicator));
    context.push(new NetworkTrafficTrackingScope());
    context.push(new SharedStringPoolScope());

    BlazeUserSettings userSettings = BlazeUserSettings.getInstance();
    context
        .push(
            new ToolWindowScope.Builder(project, task)
                .setProgressIndicator(indicator)
                .setPopupBehavior(
                    syncParams.backgroundSync()
                        ? FocusBehavior.NEVER
                        : userSettings.getShowBlazeConsoleOnSync())
                .setIssueParsers(
                    BlazeIssueParser.defaultIssueParsers(
                        project, WorkspaceRoot.fromProject(project), ContextType.Sync))
                .build())
        .push(
            new ProblemsViewScope(
                project,
                syncParams.backgroundSync()
                    ? FocusBehavior.NEVER
                    : userSettings.getShowProblemsViewOnSync(),
                /* resetProblemsContext= */ clearProblems))
        .push(new IdeaLogScope());
    if (notifyFinished
        && !syncParams.backgroundSync()
        && syncParams.syncMode() != SyncMode.NO_BUILD) {
      context.push(
          new NotificationScope(project, "Sync", "Sync project", "Sync successful", "Sync failed"));
    }

    context.output(new StatusOutput(String.format("Syncing project: %s...", syncParams)));
  }

  private static void fillInBuildStats(
      SyncStats.Builder stats,
      @Nullable SyncProjectState projectState,
      @Nullable BlazeSyncBuildResult buildResult) {
    if (projectState != null) {
      stats
          .setWorkspaceType(projectState.getLanguageSettings().getWorkspaceType())
          .setLanguagesActive(projectState.getLanguageSettings().getActiveLanguages())
          .setBlazeProjectFiles(
              projectState.getProjectViewSet().listScalarItems(ImportSection.KEY));
    }
    if (buildResult != null) {
      buildResult
          .getBuildPhaseStats()
          .forEach(
              s -> {
                stats.addBuildPhaseStats(s);
                stats.addTimedEvents(s.timedEvents());
              });
    }
  }

  private static List<TimedEvent> updateInMemoryState(
      Project project,
      BlazeContext parentContext,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      SyncMode syncMode) {
    List<TimedEvent> timedEvents = new ArrayList<>();
    Scope.push(
        parentContext,
        context -> {
          context.push(
              new TimingScope("UpdateInMemoryState", EventType.Other)
                  .addScopeListener((events, duration) -> timedEvents.addAll(events)));
          context.output(new StatusOutput("Updating in-memory state..."));
          Module workspaceModule =
              ModuleFinder.getInstance(project)
                  .findModuleByName(BlazeDataStorage.WORKSPACE_MODULE_NAME);
          for (BlazeSyncPlugin blazeSyncPlugin : BlazeSyncPlugin.EP_NAME.getExtensions()) {
            blazeSyncPlugin.updateInMemoryState(
                project,
                context,
                WorkspaceRoot.fromProject(project),
                projectViewSet,
                blazeProjectData,
                workspaceModule,
                syncMode);
          }
        });
    return timedEvents;
  }

  private static void onSyncStart(Project project, BlazeContext parentContext, SyncMode syncMode) {
    SyncScope.push(
        parentContext,
        context -> {
          for (SyncListener listener : SyncListener.EP_NAME.getExtensions()) {
            listener.onSyncStart(project, context, syncMode);
          }
        });
  }

  private static void afterSync(
      Project project,
      BlazeSyncParams syncParams,
      BlazeContext context,
      SyncResult syncResult,
      ImmutableSet<Integer> buildIds) {
    final SyncListener[] syncListeners = SyncListener.EP_NAME.getExtensions();
    for (SyncListener syncListener : syncListeners) {
      syncListener.afterSync(project, context, syncParams.syncMode(), syncResult, buildIds);
    }
  }

  private static void onSyncComplete(
      Project project,
      BlazeContext context,
      ProjectViewSet projectViewSet,
      ImmutableSet<Integer> buildIds,
      BlazeProjectData blazeProjectData,
      BlazeSyncParams syncParams,
      SyncResult syncResult) {
    validate(project, context, blazeProjectData);
    final SyncListener[] syncListeners = SyncListener.EP_NAME.getExtensions();
    for (SyncListener syncListener : syncListeners) {
      syncListener.onSyncComplete(
          project,
          context,
          BlazeImportSettingsManager.getInstance(project).getImportSettings(),
          projectViewSet,
          buildIds,
          blazeProjectData,
          syncParams.syncMode(),
          syncResult);
    }
  }

  private static void validate(
      Project project, BlazeContext context, BlazeProjectData blazeProjectData) {
    for (BlazeSyncPlugin syncPlugin : BlazeSyncPlugin.EP_NAME.getExtensions()) {
      boolean unused = syncPlugin.validate(project, context, blazeProjectData);
    }
  }

  private static Duration totalBlazeTime(ImmutableList<TimedEvent> timedEvents) {
    long totalMillis =
        timedEvents.stream()
            .filter(e -> e.isLeafEvent)
            .filter(e -> e.type == EventType.BlazeInvocation)
            .mapToLong(e -> e.duration.toMillis())
            .sum();
    return Duration.ofMillis(totalMillis);
  }

  private static void outputTimingSummary(
      BlazeContext context, ImmutableList<TimedEvent> timedEvents) {
    Map<EventType, Long> totalTimes = new LinkedHashMap<>();
    for (EventType type : EventType.values()) {
      long totalTimeMillis =
          timedEvents.stream()
              .filter(e -> e.isLeafEvent && e.type == type)
              .mapToLong(e -> e.duration.toMillis())
              .sum();
      totalTimes.put(type, totalTimeMillis);
    }
    if (totalTimes.values().stream().mapToLong(l -> l).sum() < 1000) {
      return;
    }

    String summary =
        totalTimes.entrySet().stream()
            .map(e -> String.format("%s: %s", e.getKey(), durationStr(e.getValue())))
            .collect(joining(", "));

    context.output(PrintOutput.log("\nTiming summary:\n" + summary));
  }

  private static String durationStr(long timeMillis) {
    return timeMillis >= 1000
        ? String.format("%.1fs", timeMillis / 1000d)
        : String.format("%sms", timeMillis);
  }

  private static void logSyncError(BlazeContext context, Throwable e) {
    // ignore ProcessCanceledException
    Throwable cause = e;
    while (cause != null) {
      if (cause instanceof ProcessCanceledException) {
        return;
      }
      if (cause instanceof SyncCanceledException) {
        return;
      }
      if (cause instanceof SyncFailedException) {
        if (cause.getMessage() != null) {
          IssueOutput.error(cause.getMessage()).submit(context);
        }
        logger.warn("Sync failed", cause);
        return;
      }
      cause = cause.getCause();
    }
    logger.error(e);
    IssueOutput.error("Internal error: " + e.getMessage()).submit(context);
  }
}
