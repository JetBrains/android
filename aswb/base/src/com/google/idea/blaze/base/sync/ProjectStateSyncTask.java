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

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.idea.blaze.base.async.FutureUtil;
import com.google.idea.blaze.base.async.FutureUtil.FutureResult;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.command.info.BlazeInfoRunner;
import com.google.idea.blaze.base.execution.ExecutionDeniedException;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.plugin.BuildSystemVersionChecker;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.ProjectViewVerifier;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.SyncScope.SyncCanceledException;
import com.google.idea.blaze.base.sync.SyncScope.SyncFailedException;
import com.google.idea.blaze.base.sync.projectview.LanguageSupport;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.blaze.base.vcs.BlazeVcsHandlerProvider;
import com.google.idea.blaze.base.vcs.BlazeVcsHandlerProvider.BlazeVcsHandler;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.exception.BuildException;
import com.intellij.openapi.project.Project;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Collects information about the project state (VCS, blaze info, .blazeproject contents, etc.). */
final class ProjectStateSyncTask {

  static SyncProjectState collectProjectState(Project project, BlazeContext context)
      throws SyncCanceledException, SyncFailedException {
    ProjectStateSyncTask task = new ProjectStateSyncTask(project);
    return task.getProjectState(context);
  }

  private final Project project;
  private final BlazeImportSettings importSettings;
  private final WorkspaceRoot workspaceRoot;

  private ProjectStateSyncTask(Project project) {
    this.project = project;
    this.importSettings = BlazeImportSettingsManager.getInstance(project).getImportSettings();
    this.workspaceRoot = WorkspaceRoot.fromImportSettings(importSettings);
  }

  private SyncProjectState getProjectState(BlazeContext context)
      throws SyncFailedException, SyncCanceledException {
    if (!FileOperationProvider.getInstance().exists(workspaceRoot.directory())) {
      String message = String.format("Workspace '%s' doesn't exist.", workspaceRoot.directory());
      IssueOutput.error(message).submit(context);
      BlazeSyncManager.printAndLogError(message, context);
      throw new SyncFailedException();
    }

    BlazeVcsHandler vcsHandler = BlazeVcsHandlerProvider.vcsHandlerForProject(project);
    if (vcsHandler == null) {
      String message = "Could not find a VCS handler";
      IssueOutput.error(message).submit(context);
      BlazeSyncManager.printAndLogError("Could not find a VCS handler", context);
      throw new SyncFailedException();
    }

    ListeningExecutorService executor = BlazeExecutor.getInstance().getExecutor();
    WorkspacePathResolverAndProjectView workspacePathResolverAndProjectView =
        computeWorkspacePathResolverAndProjectView(context, vcsHandler, executor);
    if (workspacePathResolverAndProjectView == null) {
      BlazeSyncManager.printAndLogError(
          "Sync failed: Could not resolve the workspace path and/or parse the project view",
          context);
      throw new SyncFailedException();
    }
    ProjectViewSet projectViewSet = workspacePathResolverAndProjectView.projectViewSet;

    List<String> syncFlags =
        BlazeFlags.blazeFlags(
            project,
            projectViewSet,
            BlazeCommandName.INFO,
            context,
            BlazeInvocationContext.SYNC_CONTEXT);

    ListenableFuture<BlazeInfo> blazeInfoFuture =
        BlazeInfoRunner.getInstance()
            .runBlazeInfo(
                project,
                Blaze.getBuildSystemProvider(project)
                    .getBuildSystem()
                    .getDefaultInvoker(project, context),
                context,
                importSettings.getBuildSystem(),
                syncFlags);

    ListenableFuture<WorkingSet> workingSetFuture = vcsHandler.getWorkingSet(context, executor);

    FutureResult<BlazeInfo> blazeInfoResult =
        FutureUtil.waitForFuture(context, blazeInfoFuture)
            .timed(Blaze.buildSystemName(project) + "Info", EventType.BlazeInvocation)
            .withProgressMessage(
                String.format("Running %s info...", Blaze.buildSystemName(project)))
            .onError(String.format("Could not run %s info", Blaze.buildSystemName(project)))
            .run();

    BlazeInfo blazeInfo = blazeInfoResult.result();
    if (blazeInfo == null) {
      Exception exception = blazeInfoResult.exception();
      if (exception != null) {
        Throwable cause = exception.getCause();
        if (cause instanceof BuildException
            && cause.getCause() instanceof ExecutionDeniedException) {
          throw new SyncCanceledException();
        }
      }
      throw new SyncFailedException();
    }
    BlazeVersionData blazeVersionData =
        BlazeVersionData.build(
            Blaze.getBuildSystemProvider(project).getBuildSystem(), workspaceRoot, blazeInfo);

    if (!BuildSystemVersionChecker.verifyVersionSupported(context, blazeVersionData)) {
      throw new SyncFailedException();
    }

    WorkspacePathResolver workspacePathResolver =
        workspacePathResolverAndProjectView.workspacePathResolver;
    WorkspaceLanguageSettings workspaceLanguageSettings =
        LanguageSupport.createWorkspaceLanguageSettings(projectViewSet);

    if (!ProjectViewVerifier.verifyProjectView(
        project, context, workspacePathResolver, projectViewSet, workspaceLanguageSettings)) {
      BlazeSyncManager.printAndLogError("Sync failed: Could not verify the project view", context);
      throw new SyncFailedException();
    }

    WorkingSet workingSet =
        FutureUtil.waitForFuture(context, workingSetFuture)
            .timed("WorkingSet", EventType.Other)
            .withProgressMessage("Computing VCS working set...")
            .onError("Could not compute working set")
            .run()
            .result();
    if (context.isCancelled()) {
      throw new SyncCanceledException();
    }
    if (context.hasErrors()) {
      BlazeSyncManager.printAndLogError("Sync failed: Could not compute working set", context);
      throw new SyncFailedException();
    }

    if (workingSet != null) {
      printWorkingSet(context, workingSet);
    }
    return SyncProjectState.builder()
        .setProjectViewSet(projectViewSet)
        .setLanguageSettings(workspaceLanguageSettings)
        .setBlazeVersionData(blazeVersionData)
        .setWorkingSet(workingSet)
        .setWorkspacePathResolver(workspacePathResolver)
        .build();
  }

  private static class WorkspacePathResolverAndProjectView {
    final WorkspacePathResolver workspacePathResolver;
    final ProjectViewSet projectViewSet;

    WorkspacePathResolverAndProjectView(
        WorkspacePathResolver workspacePathResolver, ProjectViewSet projectViewSet) {
      this.workspacePathResolver = workspacePathResolver;
      this.projectViewSet = projectViewSet;
    }
  }

  @Nullable
  private WorkspacePathResolverAndProjectView computeWorkspacePathResolverAndProjectView(
      BlazeContext context, BlazeVcsHandler vcsHandler, ListeningExecutorService executor) {
    context.output(new StatusOutput("Updating VCS..."));

    for (int i = 0; i < 3; ++i) {
      WorkspacePathResolver vcsWorkspacePathResolver = null;
      BlazeVcsHandlerProvider.BlazeVcsSyncHandler vcsSyncHandler = vcsHandler.createSyncHandler();
      if (vcsSyncHandler != null) {
        boolean ok =
            Scope.push(
                context,
                (childContext) -> {
                  childContext.push(new TimingScope("UpdateVcs", EventType.Other));
                  return vcsSyncHandler.update(context, executor);
                });
        if (!ok) {
          return null;
        }
        vcsWorkspacePathResolver = vcsSyncHandler.getWorkspacePathResolver();
      }

      WorkspacePathResolver workspacePathResolver =
          vcsWorkspacePathResolver != null
              ? vcsWorkspacePathResolver
              : new WorkspacePathResolverImpl(workspaceRoot);

      ProjectViewSet projectViewSet;
      try {
        projectViewSet =
            ProjectViewManager.getInstance(project)
                .reloadProjectView(context, workspacePathResolver);
      } catch (BuildException e) {
        context.handleException("Failed to load project view", e);
        return null;
      }

      if (vcsSyncHandler != null) {
        BlazeVcsHandlerProvider.BlazeVcsSyncHandler.ValidationResult validationResult =
            vcsSyncHandler.validateProjectView(context, projectViewSet);
        switch (validationResult) {
          case OK:
            // Fall-through and return
            break;
          case Error:
            return null;
          case RestartSync:
            continue;
          default:
            // Cannot happen
            return null;
        }
      }

      return new WorkspacePathResolverAndProjectView(workspacePathResolver, projectViewSet);
    }
    return null;
  }

  private static void printWorkingSet(BlazeContext context, WorkingSet workingSet) {
    List<String> messages = Lists.newArrayList();
    messages.addAll(
        workingSet.addedFiles.stream()
            .map(file -> file.relativePath() + " (added)")
            .collect(Collectors.toList()));
    messages.addAll(
        workingSet.modifiedFiles.stream()
            .map(file -> file.relativePath() + " (modified)")
            .collect(Collectors.toList()));
    Collections.sort(messages);

    if (messages.isEmpty()) {
      context.output(PrintOutput.log("Your working set is empty"));
      return;
    }
    int maxFiles = 20;
    for (String message : Iterables.limit(messages, maxFiles)) {
      context.output(PrintOutput.log("  " + message));
    }
    if (messages.size() > maxFiles) {
      context.output(PrintOutput.log(String.format("  (and %d more)", messages.size() - maxFiles)));
    }
    context.output(PrintOutput.output(""));
  }
}
