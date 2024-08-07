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
package com.google.idea.blaze.base.run.processhandler;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.process.BinaryPathRemapper;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.filecache.FileCaches;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.google.idea.blaze.base.util.ProcessGroupUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Platform;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.List;

/**
 * Scoped process handler.
 *
 * <p>A context is created during construction and is ended when the process is terminated.
 */
public final class ScopedBlazeProcessHandler extends KillableColoredProcessHandler {
  /**
   * Methods to give the caller of {@link ScopedBlazeProcessHandler} hooks after the context is
   * created.
   */
  public interface ScopedProcessHandlerDelegate {
    /**
     * This method is called when the process starts. Any context setup (like pushing scopes on the
     * context) should be done here.
     */
    void onBlazeContextStart(BlazeContext context);

    /** Get a list of process listeners to add to the process. */
    ImmutableList<ProcessListener> createProcessListeners(BlazeContext context);
  }

  private final ScopedProcessHandlerDelegate scopedProcessHandlerDelegate;
  private final BlazeContext context;

  /**
   * Construct a process handler and a context to be used for the life of the process.
   *
   * @param blazeCommand the blaze command to run
   * @param workspaceRoot workspace root
   * @param scopedProcessHandlerDelegate delegate methods that will be run with the process's
   *     context.
   * @throws ExecutionException
   */
  public ScopedBlazeProcessHandler(
      Project project,
      BlazeCommand blazeCommand,
      WorkspaceRoot workspaceRoot,
      ScopedProcessHandlerDelegate scopedProcessHandlerDelegate)
      throws ExecutionException {
    this(project, blazeCommand.toList(), workspaceRoot, scopedProcessHandlerDelegate);
  }

  public ScopedBlazeProcessHandler(
      Project project,
      List<String> command,
      WorkspaceRoot workspaceRoot,
      ScopedProcessHandlerDelegate scopedProcessHandlerDelegate)
      throws ExecutionException {
    super(
        ProcessGroupUtil.newProcessGroupFor(
            new CommandLineWithRemappedPath(command)
                .withWorkDirectory(workspaceRoot.directory().getPath())
                .withRedirectErrorStream(true)));

    this.scopedProcessHandlerDelegate = scopedProcessHandlerDelegate;
    this.context = BlazeContext.create();
    // The context is released in the ScopedProcessHandlerListener.
    this.context.hold();

    for (ProcessListener processListener :
        scopedProcessHandlerDelegate.createProcessListeners(context)) {
      addProcessListener(processListener);
    }
    addProcessListener(new ScopedProcessHandlerListener(project));
  }

  /**
   * Handle the {@link BlazeContext} held in a {@link ScopedBlazeProcessHandler}. This class will
   * take care of calling methods when the process starts and freeing the context when the process
   * terminates.
   */
  private class ScopedProcessHandlerListener extends ProcessAdapter {

    private final Project project;

    ScopedProcessHandlerListener(Project project) {
      this.project = project;
    }

    @Override
    public void startNotified(ProcessEvent event) {
      scopedProcessHandlerDelegate.onBlazeContextStart(context);
    }

    @Override
    public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed) {
      ListenableFuture<Void> unusedFuture =
          FileCaches.refresh(
              project,
              context,
              BlazeBuildOutputs.noOutputs(BuildResult.fromExitCode(event.getExitCode())));
      context.release();
    }
  }

  private static class CommandLineWithRemappedPath extends GeneralCommandLine {
    CommandLineWithRemappedPath(List<String> command) {
      super(command);
    }

    @Override
    protected List<String> prepareCommandLine(
        String command, List<String> parameters, Platform platform) {
      String remapped = remapBinaryPath(command);
      return super.prepareCommandLine(remapped, parameters, platform);
    }
  }

  static String remapBinaryPath(String command) {
    return BinaryPathRemapper.remapBinary(command).map(File::getAbsolutePath).orElse(command);
  }
}
