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
package com.google.idea.blaze.base.bazel;

import com.google.errorprone.annotations.MustBeClosed;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandRunner;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import com.google.idea.blaze.base.command.info.BlazeInfoException;
import com.google.idea.blaze.base.logging.utils.querysync.BuildDepsStatsScope;
import com.google.idea.blaze.base.logging.utils.querysync.SyncQueryStatsScope;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResults;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.google.idea.blaze.exception.BuildException;
import com.intellij.openapi.project.Project;
import java.io.InputStream;

/**
 * A fake for {@link BlazeCommandRunner} that doesn't execute the build, but returns results from
 * the provided result helper.
 */
public class FakeBlazeCommandRunner implements BlazeCommandRunner {

  @FunctionalInterface
  public interface BuildFunction {
    BlazeBuildOutputs runBuild(BuildResultHelper buildResultHelper) throws BuildException;
  }

  private final BuildFunction resultsFunction;
  private BlazeCommand command;

  public FakeBlazeCommandRunner() {
    this(
        buildResultHelper ->
            BlazeBuildOutputs.fromParsedBepOutput(
                BuildResult.SUCCESS, buildResultHelper.getBuildOutput()));
  }

  public FakeBlazeCommandRunner(BuildFunction buildFunction) {
    this.resultsFunction = buildFunction;
  }

  @Override
  public BlazeBuildOutputs run(
      Project project,
      BlazeCommand.Builder blazeCommandBuilder,
      BuildResultHelper buildResultHelper,
      BlazeContext context)
      throws BuildException {
    command = blazeCommandBuilder.build();
    try {
      BlazeBuildOutputs blazeBuildOutputs = resultsFunction.runBuild(buildResultHelper);
      int exitCode = blazeBuildOutputs.buildResult.exitCode;
      BuildDepsStatsScope.fromContext(context).ifPresent(stats -> stats.setBazelExitCode(exitCode));
      return blazeBuildOutputs;
    } catch (GetArtifactsException e) {
      return BlazeBuildOutputs.noOutputs(BuildResult.FATAL_ERROR);
    }
  }

  @Override
  public BlazeTestResults runTest(
      Project project,
      BlazeCommand.Builder blazeCommandBuilder,
      BuildResultHelper buildResultHelper,
      BlazeContext context) {
    return BlazeTestResults.NO_RESULTS;
  }

  @Override
  public InputStream runQuery(
      Project project,
      BlazeCommand.Builder blazeCommandBuilder,
      BuildResultHelper buildResultHelper,
      BlazeContext context)
      throws BuildException {
    SyncQueryStatsScope.fromContext(context).ifPresent(stats -> stats.setBazelExitCode(0));
    return InputStream.nullInputStream();
  }

  @Override
  @MustBeClosed
  public InputStream runBlazeInfo(
      Project project,
      BlazeCommand.Builder blazeCommandBuilder,
      BuildResultHelper buildResultHelper,
      BlazeContext context)
      throws BlazeInfoException {
    return InputStream.nullInputStream();
  }

  public BlazeCommand getIssuedCommand() {
    return command;
  }
}
