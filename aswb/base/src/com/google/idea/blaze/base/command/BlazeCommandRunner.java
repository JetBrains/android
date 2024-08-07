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

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.MustBeClosed;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResults;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.exception.BuildException;
import com.intellij.openapi.project.Project;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/** Runs a blaze command. */
public interface BlazeCommandRunner {

  /**
   * Runs a blaze build command, parses the build results into a {@link BlazeBuildOutputs} object
   * using the given {@link BuildResultHelper}.
   */
  BlazeBuildOutputs run(
      Project project,
      BlazeCommand.Builder blazeCommandBuilder,
      BuildResultHelper buildResultHelper,
      BlazeContext context)
      throws BuildException;

  /**
   * Runs a blaze test command, parses the test results into a {@link BlazeTestResults} object using
   * the given {@link BuildResultHelper}.
   */
  BlazeTestResults runTest(
      Project project,
      BlazeCommand.Builder blazeCommandBuilder,
      BuildResultHelper buildResultHelper,
      BlazeContext context)
      throws BuildException;

  /**
   * Runs a blaze query command.
   *
   * @return {@link InputStream} from the stdout of the blaze invocation using the given {@link
   *     BuildResultHelper} and null if the query fails
   */
  @MustBeClosed
  InputStream runQuery(
      Project project,
      BlazeCommand.Builder blazeCommandBuilder,
      BuildResultHelper buildResultHelper,
      BlazeContext context)
      throws BuildException;

  @MustBeClosed
  InputStream runBlazeInfo(
      Project project,
      BlazeCommand.Builder blazeCommandBuilder,
      BuildResultHelper buildResultHelper,
      BlazeContext context)
      throws BuildException;

  /** Allows enabling the use of command runner for restricted set of users. */
  default boolean canUseCli() {
    return true;
  }

  /** Get a list of additional build flags. */
  default List<String> getExtraBuildFlags(BlazeCommand.Builder blazeCommandBuilder) {
    return ImmutableList.of();
  }

  default Optional<Integer> getMaxCommandLineLength() {
    return Optional.empty();
  }
}
