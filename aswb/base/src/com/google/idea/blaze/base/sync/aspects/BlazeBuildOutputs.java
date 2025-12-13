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
package com.google.idea.blaze.base.sync.aspects;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.command.buildresult.BuildResult;
import com.google.idea.blaze.base.command.buildresult.bepparser.ParsedBepOutput;
import com.google.idea.blaze.common.artifact.OutputArtifact;

/**
 * The result of a Bazel build.
 */
public interface BlazeBuildOutputs {

  /**
   * A list of the artifacts outputted by the given target to the given output group.
   *
   * <p>Note that the same artifact may be outputted by multiple targets and into multiple output groups.
   */
  ImmutableList<OutputArtifact> getOutputGroupTargetArtifacts(String outputGroup, String label);

  /**
   * A de-duplicated list of the artifacts outputted to the given output group.
   *
   * <p>Note that the same artifact may be outputted by multiple targets and into multiple output groups. Such artifacts are included in the
   * resulting list only once.
   */
  ImmutableList<OutputArtifact> getOutputGroupArtifacts(String outputGroup);

  /**
   * Label of any targets that were not build because of build errors.
   */
  ImmutableSet<String> targetsWithErrors();

  /**
   * The final status of the Bazel build.
   */
  BuildResult buildResult();

  /**
   * An obscure ID that can be used to identify the build in the external environment.
   *
   * <p><em>DO NOT</em> attempt to interpret or compare.
   */
  String idForLogging();

  String buildId();

  /**
   * @return true if the build outputs are empty.
   */
  boolean isEmpty();


  static BlazeBuildOutputs noOutputs(BuildResult buildResult) {
    return new BlazeBuildOutputs(){
      @Override
      public ImmutableList<OutputArtifact> getOutputGroupTargetArtifacts(String outputGroup,
                                                                         String label) {
        return ImmutableList.of();
      }

      @Override
      public ImmutableList<OutputArtifact> getOutputGroupArtifacts(String outputGroup) {
        return ImmutableList.of();
      }

      @Override
      public ImmutableSet<String> targetsWithErrors() {
        return ImmutableSet.of();
      }

      @Override
      public BuildResult buildResult() {
        return BuildResult.SUCCESS;
      }

      @Override
      public String idForLogging() {
        return "";
      }

      @Override
      public String buildId() {
        return "";
      }

      @Override
      public boolean isEmpty() {
        return true;
      }
    };
  }


  static BlazeBuildOutputs fromParsedBepOutput(ParsedBepOutput parsedOutput) {
    return new BlazeBuildOutputs() {
      @Override
      public ImmutableList<OutputArtifact> getOutputGroupTargetArtifacts(String outputGroup, String label) {
        return ImmutableList.copyOf(parsedOutput.getOutputGroupTargetArtifacts(outputGroup, label));
      }

      @Override
      public ImmutableList<OutputArtifact> getOutputGroupArtifacts(String outputGroup) {
        return ImmutableList.copyOf(parsedOutput.getOutputGroupArtifacts(outputGroup));
      }

      @Override
      public ImmutableSet<String> targetsWithErrors() {
        return ImmutableSet.copyOf(parsedOutput.targetsWithErrors());
      }

      @Override
      public BuildResult buildResult() {
        return BuildResult.fromExitCode(parsedOutput.buildResult());
      }

      @Override
      public String idForLogging() {
        return parsedOutput.idForLogging();
      }

      @Override
      public String buildId() {
        return "";
      }

      @Override
      public boolean isEmpty() {
        return false;
      }
    };
  }
}
