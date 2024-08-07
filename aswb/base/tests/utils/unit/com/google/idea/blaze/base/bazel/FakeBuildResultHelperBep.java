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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.buildresult.BuildFlags;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.ParsedBepOutput;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResults;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

public final class FakeBuildResultHelperBep implements BuildResultHelper {
  private static final ImmutableList.Builder<String> startupOptions = ImmutableList.builder();
  private static final ImmutableList.Builder<String> cmdlineOptions = ImmutableList.builder();
  @Nullable private final ParsedBepOutput parsedBepOutput;

  public FakeBuildResultHelperBep(
      ImmutableList<String> startupOptions, ImmutableList<String> cmdlineOptions) {
    this(startupOptions, cmdlineOptions, null);
  }

  public FakeBuildResultHelperBep(
      ImmutableList<String> startupOptions,
      ImmutableList<String> cmdlineOptions,
      @Nullable ParsedBepOutput output) {
    FakeBuildResultHelperBep.startupOptions.addAll(startupOptions);
    FakeBuildResultHelperBep.cmdlineOptions.addAll(cmdlineOptions);
    parsedBepOutput = output;
  }

  @Override
  public List<String> getBuildFlags() {
    return ImmutableList.of();
  }

  @Override
  public ParsedBepOutput getBuildOutput(Optional<String> completedBuildId)
      throws GetArtifactsException {
    if (parsedBepOutput == null) {
      throw new GetArtifactsException("Could not get artifacts from null bep");
    }

    return parsedBepOutput;
  }

  @Override
  public BlazeTestResults getTestResults(Optional<String> completedBuildId) {
    return BlazeTestResults.NO_RESULTS;
  }

  @Override
  public BuildFlags getBlazeFlags(Optional<String> completedBuildId) throws GetFlagsException {
    return new BuildFlags(startupOptions.build(), cmdlineOptions.build());
  }

  @Override
  public void close() {}
}
