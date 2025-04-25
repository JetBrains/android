/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync;

import static com.google.common.util.concurrent.Futures.immediateFuture;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.async.executor.ProgressiveTaskWithProgressIndicator;
import com.google.idea.blaze.base.command.BlazeInvocationContext.ContextType;
import com.google.idea.blaze.base.issueparser.BlazeIssueParser;
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStatsScope;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.BlazeScope;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.scopes.IdeaLogScope;
import com.google.idea.blaze.base.scope.scopes.ProblemsViewScope;
import com.google.idea.blaze.base.scope.scopes.ProgressIndicatorScope;
import com.google.idea.blaze.base.scope.scopes.ToolWindowScope;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.settings.BlazeUserSettings.FocusBehavior;
import com.google.idea.blaze.base.targetmaps.SourceToTargetMap;
import com.google.idea.blaze.base.toolwindow.Task;
import com.google.idea.blaze.base.util.SaveUtil;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.common.artifact.BuildArtifactCache;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot;
import com.google.idea.blaze.qsync.SnapshotHolder;
import com.google.idea.blaze.qsync.deps.ArtifactTracker;
import com.google.idea.blaze.qsync.project.PostQuerySyncData;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.project.TargetsToBuild;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.serviceContainer.NonInjectable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * The project component for a query based sync.
 *
 * <p>This class manages sync'ing the intelliJ project state to the state of the Bazel project in
 * the workspace, as well as building dependencies of the project.
 *
 * <p>The sync'd state of a project is represented by {@link QuerySyncProjectSnapshot}. During the
 * sync process, different parts of that are available at different phases:
 *
 * <ul>
 *   <li>{@link ProjectDefinition}: the input to the sync process that can be created from the
 *       project configuration. This class remained unchanged throughout sync.
 *   <li>{@link PostQuerySyncData}: the state after the query invocation has been made, or after a
 *       delta has been applied to that. This class is the input and output to the partial update
 *       operation, and also contains the data that will be persisted to disk over an IDE restart.
 *   <li>{@link QuerySyncProjectSnapshot}: the full project state, created in the last phase of sync
 *       from {@link PostQuerySyncData}.
 * </ul>
 */
public class QuerySyncManager implements Disposable {
  private final Logger logger = Logger.getInstance(getClass());

  public static final String NOTIFICATION_GROUP = "QuerySyncBuild";

  private final Project project;
  private final ProjectLoader loader;
  private volatile QuerySyncProject loadedProject;

  private final QuerySyncStatus syncStatus;
  private final QuerySyncAsyncFileListener fileListener;

  private final CacheCleaner cacheCleaner;

  private static final BoolExperiment showWindowOnAutomaticSyncErrors =
      new BoolExperiment("querysync.autosync.show.console.on.error", true);

  /** An enum represent the origin of a task performed by the {@link QuerySyncManager} */
  public enum TaskOrigin {
    /** Tasks run when opening a project */
    STARTUP,
    /** User-initiated tasks */
    USER_ACTION,
    /** Tasks run automatically */
    AUTOMATIC,
    UNKNOWN
  }

  /** An enum represent the kinds of operations initiated by the sync manager */
  public enum OperationType {
    SYNC,
    BUILD_DEPS,
    OTHER,
  }

  public interface ThrowingScopedOperation {
    void execute(BlazeContext context) throws BuildException;
  }

  public static QuerySyncManager getInstance(Project project) {
    return project.getService(QuerySyncManager.class);
  }

  public QuerySyncManager(Project project) {
    this(project, null);
  }

  @VisibleForTesting
  @NonInjectable
  public QuerySyncManager(Project project, @Nullable ProjectLoader loader) {
    this.project = project;
    this.loader = loader != null ? loader : createProjectLoader(project);
    this.syncStatus = new QuerySyncStatus(project);
    this.fileListener = QuerySyncAsyncFileListener.createAndListen(project, this);
    this.cacheCleaner = new CacheCleaner(project);
  }

  /**
   * Returns a URL wth more information & help about query sync, or empty if no such URL is
   * available.
   */
  public Optional<String> getQuerySyncUrl() {
    return Optional.empty();
  }

  protected ProjectLoader createProjectLoader(Project project) {
    return new ProjectLoaderImpl(project);
  }

  public ModificationTracker getProjectModificationTracker() {
    return loader.getProjectModificationTracker();
  }

  @CanIgnoreReturnValue
  public ListenableFuture<Boolean> reloadProject(
      QuerySyncActionStatsScope querySyncActionStats, TaskOrigin taskOrigin) {
    return runSync(
        "Loading project",
        "Re-loading project",
        querySyncActionStats,
        this::loadProject,
        taskOrigin);
  }

  public void loadProject(BlazeContext context) throws BuildException {
    try {
      QuerySyncProject newProject = loader.loadProject(context);
      if (!context.hasErrors()) {
        Optional<PostQuerySyncData> projectData = newProject.readSnapshotFromDisk(context);
        loadedProject = newProject;
        loadedProject.sync(context, projectData);
      }
    } catch (IOException e) {
      throw new BuildException("Failed to load project", e);
    }
  }

  public Optional<QuerySyncProject> getLoadedProject() {
    return Optional.ofNullable(loadedProject);
  }

  public Optional<QuerySyncProjectSnapshot> getCurrentSnapshot() {
    return getLoadedProject()
        .map(QuerySyncProject::getSnapshotHolder)
        .flatMap(SnapshotHolder::getCurrent);
  }

  public boolean isProjectLoaded() {
    return loadedProject != null;
  }

  private void assertProjectLoaded() {
    if (loadedProject == null) {
      throw new IllegalStateException("Project not loaded yet");
    }
  }

  public ArtifactTracker<?> getArtifactTracker() {
    assertProjectLoaded();
    return loadedProject.getArtifactTracker();
  }

  public RenderJarArtifactTracker getRenderJarArtifactTracker() {
    assertProjectLoaded();
    return loadedProject.getRenderJarArtifactTracker();
  }

  public AppInspectorArtifactTracker getAppInspectorArtifactTracker() {
    assertProjectLoaded();
    return loadedProject.getAppInspectorArtifactTracker();
  }

  public SourceToTargetMap getSourceToTargetMap() {
    assertProjectLoaded();
    return loadedProject.getSourceToTargetMap();
  }

  public QuerySyncAsyncFileListener getFileListener() {
    return fileListener;
  }

  @CanIgnoreReturnValue
  public ListenableFuture<Boolean> onStartup(QuerySyncActionStatsScope querySyncActionStats) {
    return runSync(
        "Loading project",
        "Initializing project structure",
        querySyncActionStats,
        this::loadProject,
        TaskOrigin.STARTUP);
  }

  @CanIgnoreReturnValue
  public ListenableFuture<Boolean> fullSync(
      QuerySyncActionStatsScope querySyncActionStats, TaskOrigin taskOrigin) {
    return runSync(
        "Updating project structure",
        "Re-importing project",
        querySyncActionStats,
        context -> {
          if (!isProjectLoaded()) {
            loadProject(context);
          } else if (projectDefinitionHasChanged(context)) {
            context.output(PrintOutput.log("Project definition has changed, reloading."));
            loadProject(context);
          } else {
            loadedProject.fullSync(context);
          }
        },
        taskOrigin);
  }

  @CanIgnoreReturnValue
  public ListenableFuture<Boolean> deltaSync(
      QuerySyncActionStatsScope querySyncActionStats, TaskOrigin taskOrigin) {
    return runSync(
        "Updating project structure",
        "Refreshing project",
        querySyncActionStats,
        context -> {
          if (!isProjectLoaded()) {
            loadProject(context);
          } else if (projectDefinitionHasChanged(context)) {
            context.output(PrintOutput.log("Project definition has changed, reloading."));
            loadProject(context);
          } else {
            loadedProject.deltaSync(context);
          }
        },
        taskOrigin);
  }

  public ListenableFuture<Boolean> syncQueryDataIfNeeded(Collection<Path> workspaceRelativePaths, QuerySyncActionStatsScope querySyncActionStats, TaskOrigin taskOrigin) {
    assertProjectLoaded();
    return runSync(
        "Updating build structure",
        "Refreshing build structure",
        querySyncActionStats,
        context -> {
          if (getFileListener().hasModifiedBuildFiles() ||
              getTargetsToBuildByPaths(workspaceRelativePaths).stream().anyMatch(TargetsToBuild::requiresQueryDataRefresh)) {
            Optional<PostQuerySyncData> lastQuery = Optional.ofNullable(loadedProject)
              .flatMap(it ->
                         it.getSnapshotHolder().getCurrent().map(QuerySyncProjectSnapshot::queryData));
            if (projectDefinitionHasChanged(context)) {
              context.output(PrintOutput.log("Project definition has changed, reloading."));
              QuerySyncProject newlyLoadedProject = loader.loadProject(context);
              if (!context.hasErrors()) {
                loadedProject = newlyLoadedProject;
              }
            }
            loadedProject.syncQueryData(context, lastQuery);
          }
        },
        taskOrigin);
  }

  public ListenableFuture<Boolean> runBuild(
      String title,
      String subTitle,
      QuerySyncActionStatsScope querySyncActionStatsScope,
      ThrowingScopedOperation operation,
      TaskOrigin taskOrigin) {
    return run(
        title,
        subTitle,
        querySyncActionStatsScope,
        operation,
        taskOrigin,
        OperationType.BUILD_DEPS);
  }

  private ListenableFuture<Boolean> runSync(
      String title,
      String subTitle,
      QuerySyncActionStatsScope querySyncActionStatsScope,
      ThrowingScopedOperation operation,
      TaskOrigin taskOrigin) {
    return run(
        title, subTitle, querySyncActionStatsScope, operation, taskOrigin, OperationType.SYNC);
  }

  private ListenableFuture<Boolean> run(
      String title,
      String subTitle,
      QuerySyncActionStatsScope querySyncActionStatsScope,
      ThrowingScopedOperation operation,
      TaskOrigin taskOrigin,
      OperationType operationType) {
    SettableFuture<Boolean> result = SettableFuture.create();
    syncStatus.operationStarted(operationType);
    Futures.addCallback(
        result,
        new FutureCallback<Boolean>() {
          @Override
          public void onSuccess(Boolean success) {
            if (success) {
              syncStatus.operationEnded();
            } else {
              syncStatus.operationFailed();
            }
          }

          @Override
          public void onFailure(Throwable throwable) {
            if (result.isCancelled()) {
              syncStatus.operationCancelled();
            } else {
              syncStatus.operationFailed();
              logger.error("Sync failed", throwable);
            }
          }
        },
        MoreExecutors.directExecutor());
    try {
      ListenableFuture<Boolean> innerResultFuture =
          createAndSubmitRunTask(title, subTitle, querySyncActionStatsScope, operation, taskOrigin);
      result.setFuture(innerResultFuture);
    } catch (Throwable t) {
      result.setException(t);
      throw t;
    }
    return result;
  }

  private ListenableFuture<Boolean> createAndSubmitRunTask(
      String title,
      String subTitle,
      QuerySyncActionStatsScope querySyncActionStatsScope,
      ThrowingScopedOperation operation,
      TaskOrigin taskOrigin) {
    querySyncActionStatsScope.getBuilder().setTaskOrigin(taskOrigin);
    BlazeUserSettings userSettings = BlazeUserSettings.getInstance();
    return ProgressiveTaskWithProgressIndicator.builder(project, title)
        .submitTaskWithResult(
            indicator ->
                Scope.root(
                    context -> {
                      Task task = new Task(project, subTitle, Task.Type.SYNC);
                      BlazeScope scope =
                          new ToolWindowScope.Builder(project, task)
                              .setProgressIndicator(indicator)
                              .showSummaryOutput()
                              .setPopupBehavior(
                                  taskOrigin == TaskOrigin.AUTOMATIC
                                      ? showWindowOnAutomaticSyncErrors.getValue()
                                          ? FocusBehavior.ON_ERROR
                                          : FocusBehavior.NEVER
                                      : userSettings.getShowBlazeConsoleOnSync())
                              .setIssueParsers(
                                  BlazeIssueParser.defaultIssueParsers(
                                      project,
                                      WorkspaceRoot.fromProject(project),
                                      ContextType.Sync))
                              .build();
                      context
                          .push(new ProgressIndicatorScope(indicator))
                          .push(scope)
                          .push(querySyncActionStatsScope)
                          .push(
                              new ProblemsViewScope(
                                  project, userSettings.getShowProblemsViewOnSync()))
                          .push(new IdeaLogScope());
                      try {
                        operation.execute(context);
                      } catch (Exception e) {
                        context.handleException(title + " failed", e);
                      }
                      return !context.hasErrors();
                    }));
  }

  public Set<TargetsToBuild> getTargetsToBuildByPaths(Collection<Path> workspaceRelativePaths) {
    // TODO(mathewi) passing an empty BlazeContext here means that messages generated by
    //   DependencyTracker.getProjectTargets are now lost. They should probably be reported via
    //   an exception, or inside TargetsToBuild, so that the UI layer can decide how to display
    //   the messages.
    return loadedProject.getProjectTargets(BlazeContext.create(), workspaceRelativePaths);
  }

  @CanIgnoreReturnValue
  public ListenableFuture<Boolean> enableAnalysis(
      Set<Label> targets, QuerySyncActionStatsScope querySyncActionStats, TaskOrigin taskOrigin) {
    assertProjectLoaded();
    if (targets.isEmpty()) {
      return immediateFuture(true);
    }
    return runBuild(
        "Building dependencies",
        "Building...",
        querySyncActionStats,
        context -> loadedProject.enableAnalysis(context, targets),
        taskOrigin);
  }

  @CanIgnoreReturnValue
  public ListenableFuture<Boolean> enableAnalysisForReverseDeps(
      Set<Label> targets, QuerySyncActionStatsScope querySyncActionStats, TaskOrigin taskOrigin) {
    assertProjectLoaded();
    if (targets.isEmpty()) {
      return immediateFuture(true);
    }
    return runBuild(
        "Building dependencies for affected targets",
        "Building...",
        querySyncActionStats,
        context ->
            loadedProject.enableAnalysis(context, loadedProject.getTargetsDependingOn(targets)),
        taskOrigin);
  }

  @CanIgnoreReturnValue
  public ListenableFuture<Boolean> enableAnalysisForWholeProject(
      QuerySyncActionStatsScope querySyncActionStats, TaskOrigin taskOrigin) {
    assertProjectLoaded();
    return runBuild(
        "Enabling analysis for all project targets",
        "Building dependencies",
        querySyncActionStats,
        loadedProject::enableAnalysis,
        taskOrigin);
  }

  @CanIgnoreReturnValue
  public ListenableFuture<Boolean> clearAllDependencies(
      QuerySyncActionStatsScope querySyncActionStats, TaskOrigin taskOrigin) {
    return run(
        "Clearing dependencies",
        "Removing all built dependencies",
        querySyncActionStats,
        loadedProject::cleanDependencies,
        taskOrigin,
        OperationType.OTHER);
  }

  @CanIgnoreReturnValue
  public ListenableFuture<Boolean> resetQuerySyncState(
      QuerySyncActionStatsScope querySyncActionStats, TaskOrigin taskOrigin) {
    return run(
        "Resetting query sync",
        "Clearing artifacts and running full query",
        querySyncActionStats,
        loadedProject::resetQuerySyncState,
        taskOrigin,
        OperationType.OTHER);
  }

  public boolean canEnableAnalysisFor(Path workspaceRelativePath) {
    if (loadedProject == null) {
      return false;
    }
    return loadedProject.canEnableAnalysisFor(workspaceRelativePath);
  }

  public boolean operationInProgress() {
    return syncStatus.operationInProgress();
  }

  public Optional<OperationType> currentOperation() {
    return syncStatus.currentOperation();
  }

  public Optional<Boolean> isProjectFileAddedSinceSync(Path absolutePath) {
    if (loadedProject == null) {
      return Optional.empty();
    }
    return loadedProject.projectFileAddedSinceSync(absolutePath);
  }

  public boolean isReadyForAnalysis(PsiFile psiFile) {
    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) {
      return true;
    }
    Path nioPath = virtualFile.getFileSystem().getNioPath(virtualFile);
    if (nioPath == null) {
      return true;
    }
    return CachedValuesManager.getCachedValue(psiFile, () -> {
      boolean result;
      if (loadedProject == null) {
        result = false;
      } else {
        result = loadedProject.isReadyForAnalysis(nioPath);
      }
      return CachedValueProvider.Result.create(result, getProjectModificationTracker());
    });
  }

  /**
   * Loads the {@link ProjectViewSet} and checks if the {@link ProjectDefinition} for the project
   * has changed.
   *
   * @return true if the {@link ProjectDefinition} has changed.
   */
  private boolean projectDefinitionHasChanged(BlazeContext context) throws BuildException {
    if (loadedProject == null) {
      return true;
    }
    // Ensure edits to the project view and any imports have been saved
    SaveUtil.saveAllFiles();
    final var projectDefinition = loader.loadProjectDefinition(context).definition();
    return !loadedProject.getProjectDefinition().equals(projectDefinition);
  }

  /** Displays error notification popup balloon in IDE. */
  public void notifyError(String title, String content) {
    notifyInternal(title, content, NotificationType.ERROR);
  }

  /** Displays warning notification popup balloon in IDE. */
  public void notifyWarning(String title, String content) {
    notifyInternal(title, content, NotificationType.WARNING);
  }

  private void notifyInternal(String title, String content, NotificationType notificationType) {
    Notifications.Bus.notify(
        new Notification(NOTIFICATION_GROUP, title, content, notificationType), project);
  }

  /**
   * Called by the build artifact cache to request that it is cleaned at some point in the future.
   */
  BuildArtifactCache.CleanRequest cacheCleanRequest() {
    return cacheCleaner;
  }

  public void cleanCacheNow() {
    cacheCleaner.cleanNow();
  }

  public void purgeBuildCache(QuerySyncActionStatsScope actionScope) {
    run(
        "Purging build cache",
        "Deleting all cached build artifacts",
        actionScope,
        c -> getLoadedProject().orElseThrow().getBuildArtifactCache().purge(),
        TaskOrigin.USER_ACTION,
        OperationType.OTHER);
  }

  @Override
  public void dispose() {}
}
