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

import com.android.annotations.concurrency.WorkerThread;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.executor.ProgressiveTaskWithProgressIndicator;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.buildresult.BuildResult;
import com.google.idea.blaze.base.experiments.ExperimentScope;
import com.google.idea.blaze.base.issueparser.BlazeIssueParser;
import com.google.idea.blaze.base.model.BlazeProjectData;
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
import com.google.idea.blaze.base.sync.aspects.BlazeIdeInterface;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.toolwindow.Task;
import com.google.idea.blaze.base.util.SaveUtil;
import com.google.idea.blaze.common.Label;
import com.intellij.openapi.project.Project;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Utility to build various collections of targets. */
public class BlazeBuildService {
  public static BlazeBuildService getInstance(Project project) {
    return project.getService(BlazeBuildService.class);
  }

  private final Project project;
  private final BuildSystem buildSystem;

  public BlazeBuildService(Project project) {
    this.project = project;
    this.buildSystem = Blaze.getBuildSystemProvider(project).getBuildSystem();
  }

  public ListenableFuture<Boolean> buildFileForLabels(
      String displayFileName, Set<? extends Label> labels) {
    if (!Blaze.isBlazeProject(project) || displayFileName == null) {
      return Futures.immediateFuture(false);
    }
    List<String> targets = labels.stream().map(Label::toString).toList();
    return submitTask(project, context -> buildFileTask(displayFileName, targets, context));
  }

  private boolean buildFileTask(String displayFileName, List<? extends String> targets, BlazeContext context1) {
    ProjectViewSet projectView = ProjectViewManager.getInstance(project).getProjectViewSet();
    BlazeProjectData projectData =
      BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectView == null || projectData == null) {
      return false;
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

  public ListenableFuture<Boolean> buildProject() {
    if (!Blaze.isBlazeProject(project)) {
      return Futures.immediateFuture(false);
    }
    return submitTask(project, this::runBuildProjectTask);
 }

  public  Boolean runBuildProjectTask(BlazeContext context) {
    ProjectViewSet projectView = ProjectViewManager.getInstance(project).getProjectViewSet();
    BlazeProjectData projectData =
      BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectView == null || projectData == null) {
      return null;
    }

    ScopedFunction<List<? extends String>> targets =
      context1 -> {
        try {
          return SyncProjectTargetsHelper.getProjectTargets(
              project,
              context1,
              projectView,
              projectData.getWorkspacePathResolver(),
              projectData.getWorkspaceLanguageSettings())
            .getTargetsToSync().stream().map(TargetExpression::toString).toList();
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
    Task task = new Task(project, taskName);
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
                                                    ScopedFunction<List<? extends String>> targetsFunction) {
    List<? extends String> targets = targetsFunction.execute(context);
    if (targets == null) {
      return true;
    }

    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);

    SaveUtil.saveAllFiles();
    BlazeBuildListener.EP_NAME.extensions().forEach(e -> e.buildStarting(project));

    BuildInvoker buildInvoker = buildSystem.getBuildInvoker(project);

    BuildResult buildResult =
      BlazeIdeInterface.getInstance()
        .build(
          project,
          context,
          workspaceRoot,
          projectData.getBlazeVersionData(),
          buildInvoker,
          projectView,
          targets,
          projectData.getWorkspaceLanguageSettings(),
          BlazeInvocationContext.OTHER_CONTEXT,
          false)
        .buildResult();

    notifyListeners(project, buildResult);

    if (buildResult.status != BuildResult.Status.SUCCESS) {
      context.setHasError();
    }
    return buildResult.status == BuildResult.Status.SUCCESS;
  }

  /**
   * Asynchronously refreshes the registered file caches and calls {@link
   * BlazeBuildListener#buildCompleted} after all file caches are done refreshing.
   */
  private static void notifyListeners(Project project, BuildResult buildResult) {
    BlazeBuildListener.EP_NAME
      .getExtensionList()
      .forEach(ep -> ep.buildCompleted(project, buildResult));
  }
}

