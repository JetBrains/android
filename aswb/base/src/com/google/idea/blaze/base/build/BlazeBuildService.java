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
package com.google.idea.blaze.base.build;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.annotations.concurrency.WorkerThread;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.base.async.executor.ProgressiveTaskWithProgressIndicator;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.bazel.BuildSystem.SyncStrategy;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.experiments.ExperimentScope;
import com.google.idea.blaze.base.filecache.FileCaches;
import com.google.idea.blaze.base.issueparser.BlazeIssueParser;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ScopedFunction;
import com.google.idea.blaze.base.scope.ScopedTask;
import com.google.idea.blaze.base.scope.scopes.IdeaLogScope;
import com.google.idea.blaze.base.scope.scopes.NotificationScope;
import com.google.idea.blaze.base.scope.scopes.ProblemsViewScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.scope.scopes.ToolWindowScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.settings.BlazeUserSettings.FocusBehavior;
import com.google.idea.blaze.base.sync.SyncProjectTargetsHelper;
import com.google.idea.blaze.base.sync.SyncScope.SyncCanceledException;
import com.google.idea.blaze.base.sync.SyncScope.SyncFailedException;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.base.sync.aspects.BlazeIdeInterface;
import com.google.idea.blaze.base.command.buildresult.BuildResult;
import com.google.idea.blaze.base.sync.aspects.strategy.AspectStrategy.OutputGroup;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.sharding.BlazeBuildTargetSharder;
import com.google.idea.blaze.base.sync.sharding.BlazeBuildTargetSharder.ShardedTargetsResult;
import com.google.idea.blaze.base.toolwindow.Task;
import com.google.idea.blaze.base.util.SaveUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;

/** Utility to build various collections of targets. */
public class BlazeBuildService {
  private static final Key<Long> PROJECT_LAST_BUILD_TIMESTAMP_KEY =
      Key.create("blaze.project.last.build.timestamp");

  public static BlazeBuildService getInstance(Project project) {
    return project.getService(BlazeBuildService.class);
  }

  public static Long getLastBuildTimeStamp(Project project) {
    return project.getUserData(PROJECT_LAST_BUILD_TIMESTAMP_KEY);
  }

  private final Project project;
  private final BuildSystem buildSystem;

  public BlazeBuildService(Project project) {
    this.project = project;
    this.buildSystem = Blaze.getBuildSystemProvider(project).getBuildSystem();
  }

  public ListenableFuture<Boolean> buildFileForLabels(
      String displayFileName, ImmutableSet<com.google.idea.blaze.common.Label> labels) {
    if (!Blaze.isBlazeProject(project) || displayFileName == null) {
      return null;
    }
    ImmutableCollection<Label> targets = labels.stream().map(Label::create).collect(toImmutableSet());
    return submitTask(project, context -> buildFileTask(displayFileName, targets, context));
  }

  public ListenableFuture<Boolean> buildFile(String displayFileName, ImmutableCollection<Label> targets) {
    if (!Blaze.isBlazeProject(project) || displayFileName == null) {
      return null;
    }
    return submitTask(project, context -> buildFileTask(displayFileName, targets, context));
  }

  public Boolean buildFileTask(String displayFileName, ImmutableCollection<Label> targets, BlazeContext context1) {
    ProjectViewSet projectView = ProjectViewManager.getInstance(project).getProjectViewSet();
    BlazeProjectData projectData =
      BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectView == null || projectData == null) {
      return null;
    }

    String title = "Make " + displayFileName;

    final NotificationScope make = new NotificationScope(
      project, "Make", title, title + " completed successfully", title + " failed");
    return runTaskInBuildRootScope(context1, project, title,
                                   BlazeUserSettings.getInstance()
                                     .getShowProblemsViewOnRun(), make,
                                   context2 -> buildTargetExpressionsCore(
                                     context2, project, buildSystem,
                                     projectView, projectData,
                                     context -> Lists.newArrayList(
                                       targets)));
  }

  public void buildProject() {
    if (!Blaze.isBlazeProject(project)) {
      return;
    }
    submitTask(project, this::runBuildProjectTask);

    // In case the user touched a file, but didn't change its content. The user will get a false
    // positive for class file out of date. We need a way for the user to suppress the false
    // message. Clicking the "build project" link should at least make the message go away.
    project.putUserData(PROJECT_LAST_BUILD_TIMESTAMP_KEY, System.currentTimeMillis());
  }

  public  Boolean runBuildProjectTask(BlazeContext context) {
    ProjectViewSet projectView = ProjectViewManager.getInstance(project).getProjectViewSet();
    BlazeProjectData projectData =
      BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectView == null || projectData == null) {
      return null;
    }

    ScopedFunction<List<TargetExpression>> targets =
      context1 -> {
        try {
          return SyncProjectTargetsHelper.getProjectTargets(
              project,
              context1,
              projectView,
              projectData.getWorkspacePathResolver(),
              projectData.getWorkspaceLanguageSettings())
            .getTargetsToSync();
        } catch (SyncCanceledException e) {
          context1.setCancelled();
          return null;
        } catch (SyncFailedException e) {
          context1.setHasError();
          return null;
        }
      };

    final NotificationScope notificationScope = new NotificationScope(
      project,
      "Make",
      "Make project",
      "Make project completed successfully",
      "Make project failed");
    return runTaskInBuildRootScope(context, project, "Make project",
                                   BlazeUserSettings.getInstance().getShowProblemsViewOnRun(),
                                   notificationScope,
                                   context1 -> buildTargetExpressionsCore(context1, project, buildSystem,
                                                                         projectView, projectData,
                                                                         targets));
  }

  private static ListenableFuture<Boolean> submitTask(Project project, final Function<BlazeContext, Boolean> task) {
    return ProgressiveTaskWithProgressIndicator.builder(project, "Building targets")
      .submitTaskWithResult(
        new ScopedTask<>() {
          @Override
          public Boolean execute(BlazeContext context) {
            return task.apply(context);
          }
        });
  }

  private static Boolean runTaskInBuildRootScope(BlazeContext context,
                                                 Project project,
                                                 String taskName,
                                                 FocusBehavior problemsViewFocus,
                                                 NotificationScope notificationScope,
                                                 Function<BlazeContext, Boolean> buildTask) {
    Task task = new Task(project, taskName, Task.Type.MAKE);
    context
      .push(
        new ToolWindowScope.Builder(project, task)
          .setIssueParsers(
            BlazeIssueParser.defaultIssueParsers(
              project,
              WorkspaceRoot.fromProject(project),
              BlazeInvocationContext.ContextType.Sync))
          .build())
      .push(new ExperimentScope())
      .push(new ProblemsViewScope(project, problemsViewFocus))
      .push(new IdeaLogScope())
      .push(new TimingScope("Make", EventType.BlazeInvocation))
      .push(notificationScope);

    return buildTask.apply(context);
  }

  @WorkerThread
  private static Boolean buildTargetExpressionsCore(BlazeContext context,
                                                    Project project,
                                                    BuildSystem buildSystem,
                                                    ProjectViewSet projectView,
                                                    BlazeProjectData projectData,
                                                    ScopedFunction<List<TargetExpression>> targetsFunction) {
    List<TargetExpression> targets = targetsFunction.execute(context);
    if (targets == null) {
      return true;
    }

    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);

    SaveUtil.saveAllFiles();
    BlazeBuildListener.EP_NAME.extensions().forEach(e -> e.buildStarting(project));

    BuildInvoker buildInvoker = buildSystem.getBuildInvoker(project);

    ShardedTargetsResult shardedTargets =
        BlazeBuildTargetSharder.expandAndShardTargets(
          project,
          context,
          projectView,
          projectData.getWorkspacePathResolver(),
          targets,
          buildInvoker,
          SyncStrategy.SERIAL);
    if (shardedTargets.buildResult.status == BuildResult.Status.FATAL_ERROR) {
      return false;
    }
    BlazeBuildOutputs buildOutputs =
        BlazeIdeInterface.getInstance()
            .build(
              project,
              context,
              workspaceRoot,
              projectData.getBlazeVersionData(),
              buildInvoker,
              projectView,
              shardedTargets.shardedTargets,
              projectData.getWorkspaceLanguageSettings(),
              ImmutableSet.of(OutputGroup.COMPILE),
              BlazeInvocationContext.OTHER_CONTEXT,
                shardedTargets.shardedTargets.shardCount() > 1);

    refreshFileCachesAndNotifyListeners(context, buildOutputs, project);

    if (buildOutputs.buildResult().status != BuildResult.Status.SUCCESS) {
      context.setHasError();
    }
    return buildOutputs.buildResult().status == BuildResult.Status.SUCCESS;
  }

  /**
   * Asynchronously refreshes the registered file caches and calls {@link
   * BlazeBuildListener#buildCompleted} after all file caches are done refreshing.
   */
  private static void refreshFileCachesAndNotifyListeners(
      BlazeContext context, BlazeBuildOutputs buildOutputs, Project project) {
    ListenableFuture<Void> refreshFuture = FileCaches.refresh(project, context, buildOutputs);
    // Notify the build listeners after file caches are done refreshing.
    Futures.addCallback(
        refreshFuture,
        new FutureCallback<Void>() {
          @Override
          public void onSuccess(@Nullable Void unused) {
            BlazeBuildListener.EP_NAME
                .extensions()
                .forEach(ep -> ep.buildCompleted(project, buildOutputs.buildResult()));
          }

          @Override
          public void onFailure(Throwable throwable) {
            // No additional steps for failures. The file caches notify users and
            // print logs as required.
            BlazeBuildListener.EP_NAME
                .extensions()
                .forEach(ep -> ep.buildCompleted(project, buildOutputs.buildResult()));
          }
        },
        MoreExecutors.directExecutor());
  }
}

