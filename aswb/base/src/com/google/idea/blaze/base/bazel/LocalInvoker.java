/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.bazel;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.Closer;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.async.process.PrintOutputLineProcessor;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeCommandRunner;
import com.google.idea.blaze.base.command.CommandLineBlazeCommandRunner;
import com.google.idea.blaze.base.command.WorkspaceRootReplacement;
import com.google.idea.blaze.base.command.buildresult.BuildEventProtocolUtils;
import com.google.idea.blaze.base.command.buildresult.BuildResult;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelperBep;
import com.google.idea.blaze.base.command.buildresult.bepparser.BuildEventStreamProvider;
import com.google.idea.blaze.base.console.BlazeConsoleLineProcessorProvider;
import com.google.idea.blaze.base.execution.BazelGuard;
import com.google.idea.blaze.base.execution.ExecutionDeniedException;
import com.google.idea.blaze.base.logging.utils.querysync.BuildDepsStatsScope;
import com.google.idea.blaze.base.logging.utils.querysync.SyncQueryStatsScope;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.sections.BazelBinarySection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.settings.BuildBinaryType;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.exception.BuildException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * A local Blaze/Bazel invoker that issues commands via CLI.
 */
public class LocalInvoker extends AbstractBuildInvoker1 {
  private static Logger logger = Logger.getInstance(LocalInvoker.class);
  private final BuildBinaryType buildBinaryType;

  public LocalInvoker(Project project, BlazeContext blazeContext, BuildSystem buildSystem, BuildBinaryType binaryType) {
    super(project, blazeContext, buildSystem, binaryPath(binaryType, project));
    this.buildBinaryType = binaryType;
  }

  @Override
  public BuildResultHelper createBuildResultHelper() {
    return new BuildResultHelperBep();
  }

  @Override
  public BlazeCommandRunner getCommandRunner() {
    return new CommandLineBlazeCommandRunner();
  }

  @Override
  public boolean supportsHomeBlazerc() {
    return true;
  }

  @Override
  public boolean supportsParallelism() {
    return false;
  }

  @Override
  public ImmutableSet<Capability> getCapabilities() {
    return ImmutableSet.of(Capability.IS_LOCAL, Capability.SUPPORTS_CLI);
  }

  @Override
  public BuildEventStreamProvider invoke(BlazeCommand.Builder blazeCommandBuilder)
    throws BuildException {
    try {
      performGuardCheck(project, blazeContext);
    }
    catch (ExecutionDeniedException e) {
      throw new BuildException(e.getMessage(), e);
    }
    File outputFile = BuildEventProtocolUtils.createTempOutputFile();
    BuildResult buildResult =
      issueBuild(blazeCommandBuilder, WorkspaceRoot.fromProject(project), blazeContext, outputFile);
    if (blazeCommandBuilder.build().getName().equals(BlazeCommandName.BUILD)) {
      BuildDepsStatsScope.fromContext(blazeContext)
        .ifPresent(stats -> stats.setBazelExitCode(buildResult.exitCode));
    }
    return getBepStream(outputFile);
  }

  @Override
  public InputStream invokeQuery(BlazeCommand.Builder blazeCommandBuilder) {
    try {
      performGuardCheck(project, blazeContext);
    }
    catch (ExecutionDeniedException e) {
      logger.error(e);
      return null;
    }

    BlazeCommand blazeCommand = blazeCommandBuilder.build();
    try (Closer closer = Closer.create()) {
      Path tempFile =
        Files.createTempFile(
          String.format("intellij-bazel-%s-", blazeCommand.getName()), ".stdout");
      OutputStream out = closer.register(Files.newOutputStream(tempFile));
      WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);
      Function<String, String> rootReplacement =
        WorkspaceRootReplacement.create(workspaceRoot.path(), blazeCommand);
      boolean isUnitTestMode = ApplicationManager.getApplication().isUnitTestMode();
      int retVal =
        ExternalTask.builder(workspaceRoot)
          .addBlazeCommand(blazeCommand)
          .context(blazeContext)
          .stdout(out)
          .stderr(
            LineProcessingOutputStream.of(
              line -> {
                line = rootReplacement.apply(line);
                // errors are expected, so limit logging to info level
                if (isUnitTestMode) {
                  // This is essential output in bazel-in-bazel tests if they fail.
                  System.out.println(line.stripTrailing());
                }
                Logger.getInstance(this.getClass()).info(line.stripTrailing());
                blazeContext.output(PrintOutput.output(line.stripTrailing()));
                return true;
              }))
          .ignoreExitCode(true)
          .build()
          .run();
      SyncQueryStatsScope.fromContext(blazeContext)
        .ifPresent(stats -> stats.setBazelExitCode(retVal));
      BazelExitCodeException.throwIfFailed(blazeCommand, retVal, BazelExitCodeException.ThrowOption.ALLOW_PARTIAL_SUCCESS);
      return new BufferedInputStream(
        Files.newInputStream(tempFile, StandardOpenOption.DELETE_ON_CLOSE));
    }
    catch (IOException | BuildException e) {
      logger.error(e);
      return null;
    }
  }

  @Override
  @Nullable
  public InputStream invokeInfo(BlazeCommand.Builder blazeCommandBuilder) {
    try {
      performGuardCheck(project, blazeContext);
    }
    catch (ExecutionDeniedException e) {
      logger.error(e);
      return null;
    }

    BlazeCommand blazeCommand = blazeCommandBuilder.build();
    try (Closer closer = Closer.create()) {
      Path tmpFile =
        Files.createTempFile(
          String.format("intellij-bazel-%s-", blazeCommand.getName()), ".stdout");
      OutputStream out = closer.register(Files.newOutputStream(tmpFile));
      OutputStream stderr =
        closer.register(
          LineProcessingOutputStream.of(new PrintOutputLineProcessor(blazeContext)));
      int exitCode =
        ExternalTask.builder(WorkspaceRoot.fromProject(project))
          .addBlazeCommand(blazeCommand)
          .context(blazeContext)
          .stdout(out)
          .stderr(stderr)
          .ignoreExitCode(true)
          .build()
          .run();
      BazelExitCodeException.throwIfFailed(blazeCommand, exitCode);
      return new BufferedInputStream(
        Files.newInputStream(tmpFile, StandardOpenOption.DELETE_ON_CLOSE));
    }
    catch (IOException | BuildException e) {
      logger.error(e);
      return null;
    }
  }

  @Override
  public BuildBinaryType getType() {
    return this.buildBinaryType;
  }

  private BuildResult issueBuild(
    BlazeCommand.Builder blazeCommandBuilder, WorkspaceRoot workspaceRoot, BlazeContext context, File outputFile) {
    blazeCommandBuilder.addBlazeFlags(BuildEventProtocolUtils.getBuildFlags(outputFile));
    int retVal =
      ExternalTask.builder(workspaceRoot)
        .addBlazeCommand(blazeCommandBuilder.build())
        .context(context)
        .stderr(
          LineProcessingOutputStream.of(
            BlazeConsoleLineProcessorProvider.getAllStderrLineProcessors(context)))
        .ignoreExitCode(true)
        .build()
        .run();
    return BuildResult.fromExitCode(retVal);
  }

  private BuildEventStreamProvider getBepStream(File outputFile) throws BuildResultHelper.GetArtifactsException {
    try {
      return BuildEventStreamProvider.fromInputStream(
        new BufferedInputStream(new FileInputStream(outputFile)));
    }
    catch (FileNotFoundException e) {
      logger.error(e);
      throw new BuildResultHelper.GetArtifactsException(e.getMessage());
    }
  }

  private void performGuardCheck(Project project, BlazeContext context)
    throws ExecutionDeniedException {
    try {
      BazelGuard.checkExtensionsIsExecutionAllowed(project);
    }
    catch (ExecutionDeniedException e) {
      IssueOutput.error(
          "Can't invoke "
          + Blaze.buildSystemName(project)
          + " because the project is not trusted")
        .submit(context);
      throw e;
    }
  }

  private static String binaryPath(BuildBinaryType binaryType, Project project) {
    if (binaryType.equals(BuildBinaryType.BLAZE) || binaryType.equals(BuildBinaryType.BLAZE_CUSTOM)) {
      return BlazeUserSettings.getInstance().getBlazeBinaryPath();
    }
    File projectSpecificBinary = null;
    ProjectViewSet projectView = ProjectViewManager.getInstance(project).getProjectViewSet();
    if (projectView != null) {
      projectSpecificBinary = projectView.getScalarValue(BazelBinarySection.KEY).orElse(null);
    }

    if (projectSpecificBinary != null) {
      return projectSpecificBinary.getPath();
    }
    return BlazeUserSettings.getInstance().getBazelBinaryPath();
  }
}
