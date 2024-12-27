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
package com.google.idea.blaze.base.command;

import com.google.common.collect.Interner;
import com.google.common.io.Closer;
import com.google.errorprone.annotations.MustBeClosed;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.async.process.PrintOutputLineProcessor;
import com.google.idea.blaze.base.bazel.BazelExitCodeException;
import com.google.idea.blaze.base.bazel.BazelExitCodeException.ThrowOption;
import com.google.idea.blaze.base.command.buildresult.BuildResultParser;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import com.google.idea.blaze.base.command.buildresult.bepparser.ParsedBepOutput;
import com.google.idea.blaze.base.console.BlazeConsoleLineProcessorProvider;
import com.google.idea.blaze.base.execution.BazelGuard;
import com.google.idea.blaze.base.execution.ExecutionDeniedException;
import com.google.idea.blaze.base.logging.utils.querysync.BuildDepsStatsScope;
import com.google.idea.blaze.base.logging.utils.querysync.SyncQueryStatsScope;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResults;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.scopes.SharedStringPoolScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.base.command.buildresult.BuildResult;
import com.google.idea.blaze.base.command.buildresult.BuildResult.Status;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.exception.BuildException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtilRt;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.function.Function;

/** {@inheritDoc} Start a build via local binary. */
public class CommandLineBlazeCommandRunner implements BlazeCommandRunner {

  @Override
  public BlazeBuildOutputs.Legacy runLegacy(
      Project project,
      BlazeCommand.Builder blazeCommandBuilder,
      BuildResultHelper buildResultHelper,
      BlazeContext context) {
    try {
      performGuardCheck(project, context);
    } catch (ExecutionDeniedException e) {
      return BlazeBuildOutputs.noOutputsForLegacy(BuildResult.FATAL_ERROR);
    }

    BuildResult buildResult =
        issueBuild(blazeCommandBuilder, WorkspaceRoot.fromProject(project), context);
    BuildDepsStatsScope.fromContext(context)
        .ifPresent(stats -> stats.setBazelExitCode(buildResult.exitCode));
    if (buildResult.status == Status.FATAL_ERROR) {
      return BlazeBuildOutputs.noOutputsForLegacy(buildResult);
    }
    context.output(PrintOutput.log("Build command finished. Retrieving BEP outputs..."));
    try {
      Interner<String> stringInterner =
          Optional.ofNullable(context.getScope(SharedStringPoolScope.class))
              .map(SharedStringPoolScope::getStringInterner)
              .orElse(null);
      ParsedBepOutput buildOutput;
      try (final var bepStream = buildResultHelper.getBepStream(Optional.empty())) {
        buildOutput = BuildResultParser.getBuildOutput(bepStream, stringInterner);
      }
      context.output(PrintOutput.log("BEP outputs retrieved (%s).", StringUtilRt.formatFileSize(buildOutput.getBepBytesConsumed())));
      return BlazeBuildOutputs.fromParsedBepOutputForLegacy(buildResult, buildOutput);
    } catch (GetArtifactsException e) {
      context.output(PrintOutput.log("Failed to get build outputs: " + e.getMessage()));
      context.setHasError();
      return BlazeBuildOutputs.noOutputsForLegacy(buildResult);
    }
  }

  @Override
  public BlazeBuildOutputs run(Project project, BlazeCommand.Builder blazeCommandBuilder,
                               BuildResultHelper buildResultHelper, BlazeContext context) throws BuildException {
    return runLegacy(project, blazeCommandBuilder, buildResultHelper, context);
  }

  @Override
  public BlazeTestResults runTest(
      Project project,
      BlazeCommand.Builder blazeCommandBuilder,
      BuildResultHelper buildResultHelper,
      BlazeContext context) {
    try {
      performGuardCheck(project, context);
    } catch (ExecutionDeniedException e) {
      return BlazeTestResults.NO_RESULTS;
    }

    BuildResult buildResult =
        issueBuild(blazeCommandBuilder, WorkspaceRoot.fromProject(project), context);
    if (buildResult.status == Status.FATAL_ERROR) {
      return BlazeTestResults.NO_RESULTS;
    }
    context.output(PrintOutput.log("Build command finished. Retrieving BEP outputs..."));
    try(final var bepStream = buildResultHelper.getBepStream(Optional.empty())) {
      return BuildResultParser.getTestResults(bepStream);
    } catch (GetArtifactsException e) {
      context.output(PrintOutput.log("Failed to get build outputs: " + e.getMessage()));
      context.setHasError();
      return BlazeTestResults.NO_RESULTS;
    }
  }

  @Override
  public InputStream runQuery(
      Project project,
      BlazeCommand.Builder blazeCommandBuilder,
      BuildResultHelper buildResultHelper,
      BlazeContext context)
      throws BuildException {
    performGuardCheckAsBuildException(project, context);

    try (Closer closer = Closer.create()) {
      Path tempFile =
          Files.createTempFile(
              String.format("intellij-bazel-%s-", blazeCommandBuilder.build().getName()),
              ".stdout");
      OutputStream out = closer.register(Files.newOutputStream(tempFile));
      BlazeCommand command = blazeCommandBuilder.build();
      WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);
      Function<String, String> rootReplacement =
          WorkspaceRootReplacement.create(workspaceRoot.path(), command);
      boolean isUnitTestMode = ApplicationManager.getApplication().isUnitTestMode();
      int retVal =
          ExternalTask.builder(workspaceRoot)
              .addBlazeCommand(command)
              .context(context)
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
                        context.output(PrintOutput.output(line.stripTrailing()));
                        return true;
                      }))
              .ignoreExitCode(true)
              .build()
              .run();
      SyncQueryStatsScope.fromContext(context).ifPresent(stats -> stats.setBazelExitCode(retVal));
      BazelExitCodeException.throwIfFailed(
          blazeCommandBuilder, retVal, ThrowOption.ALLOW_PARTIAL_SUCCESS);
      return new BufferedInputStream(
          Files.newInputStream(tempFile, StandardOpenOption.DELETE_ON_CLOSE));
    } catch (IOException e) {
      throw new BuildException(e);
    }
  }

  @Override
  @MustBeClosed
  public InputStream runBlazeInfo(
      Project project,
      BlazeCommand.Builder blazeCommandBuilder,
      BuildResultHelper buildResultHelper,
      BlazeContext context)
      throws BuildException {
    performGuardCheckAsBuildException(project, context);

    try (Closer closer = Closer.create()) {
      Path tmpFile =
          Files.createTempFile(
              String.format("intellij-bazel-%s-", blazeCommandBuilder.build().getName()),
              ".stdout");
      OutputStream out = closer.register(Files.newOutputStream(tmpFile));
      OutputStream stderr =
          closer.register(LineProcessingOutputStream.of(new PrintOutputLineProcessor(context)));
      int exitCode =
          ExternalTask.builder(WorkspaceRoot.fromProject(project))
              .addBlazeCommand(blazeCommandBuilder.build())
              .context(context)
              .stdout(out)
              .stderr(stderr)
              .ignoreExitCode(true)
              .build()
              .run();
      BazelExitCodeException.throwIfFailed(blazeCommandBuilder, exitCode);
      return new BufferedInputStream(
          Files.newInputStream(tmpFile, StandardOpenOption.DELETE_ON_CLOSE));
    } catch (IOException e) {
      throw new BuildException(e);
    }
  }

  private BuildResult issueBuild(
      BlazeCommand.Builder blazeCommandBuilder, WorkspaceRoot workspaceRoot, BlazeContext context) {
    blazeCommandBuilder.addBlazeFlags(getExtraBuildFlags(blazeCommandBuilder));
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

  @Override
  public Optional<Integer> getMaxCommandLineLength() {
    // Return a conservative value.
    // `getconf ARG_MAX` returns large values (1048576 on mac, 2097152 on linux) but this is
    // much larger than the actual command line limit seen in practice.
    // On linux, `xargs --show-limits` says "Size of command buffer we are actually using: 131072"
    // so choose a value somewhere south of that, which seems to work.
    return Optional.of(130000);
  }

  private void performGuardCheck(Project project, BlazeContext context)
      throws ExecutionDeniedException {
    try {
      BazelGuard.checkExtensionsIsExecutionAllowed(project);
    } catch (ExecutionDeniedException e) {
      IssueOutput.error(
              "Can't invoke "
                  + Blaze.buildSystemName(project)
                  + " because the project is not trusted")
          .submit(context);
      throw e;
    }
  }

  private void performGuardCheckAsBuildException(Project project, BlazeContext context)
      throws BuildException {
    try {
      performGuardCheck(project, context);
    } catch (ExecutionDeniedException e) {
      throw new BuildException(e);
    }
  }
}
