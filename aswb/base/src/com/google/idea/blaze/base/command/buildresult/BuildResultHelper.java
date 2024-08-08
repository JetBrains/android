/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.command.buildresult;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Interner;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResults;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import com.google.idea.blaze.exception.BuildException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Assists in getting build artifacts from a build operation. */
public interface BuildResultHelper extends AutoCloseable {

  /**
   * Returns the build flags necessary for the build result helper to work.
   *
   * <p>The user must add these flags to their build command.
   */
  List<String> getBuildFlags();

  /**
   * Parses the BEP output data and returns the corresponding {@link ParsedBepOutput}. May only be
   * called once, after the build is complete.
   *
   * <p>As BEP retrieval can be memory-intensive for large projects, implementations of
   * getBuildOutput may restrict parallelism for cases in which many builds are executed in parallel
   * (e.g. remote builds).
   */
  default ParsedBepOutput getBuildOutput() throws GetArtifactsException {
    return getBuildOutput(Optional.empty());
  }

  /**
   * Parses the BEP output data and returns the corresponding {@link ParsedBepOutput}. May only be
   * called once, after the build is complete.
   *
   * <p>As BEP retrieval can be memory-intensive for large projects, implementations of
   * getBuildOutput may restrict parallelism for cases in which many builds are executed in parallel
   * (e.g. remote builds).
   */
  default ParsedBepOutput getBuildOutput(Interner<String> stringInterner)
      throws GetArtifactsException {
    return getBuildOutput(Optional.empty(), stringInterner);
  }

  /**
   * Parses the BEP output data and returns the corresponding {@link ParsedBepOutput}. May only be
   * called once, after the build is complete.
   *
   * <p>As BEP retrieval can be memory-intensive for large projects, implementations of
   * getBuildOutput may restrict parallelism for cases in which many builds are executed in parallel
   * (e.g. remote builds).
   */
  default ParsedBepOutput getBuildOutput(
      Optional<String> completionBuildId, Interner<String> stringInterner)
      throws GetArtifactsException {
    return getBuildOutput(completionBuildId);
  }

  /**
   * Retrieves BEP build events according to given id, parses them and returns the corresponding
   * {@link ParsedBepOutput}. May only be called once, after the build is complete.
   *
   * <p>As BEP retrieval can be memory-intensive for large projects, implementations of
   * getBuildOutput may restrict parallelism for cases in which many builds are executed in parallel
   * (e.g. remote builds).
   */
  ParsedBepOutput getBuildOutput(Optional<String> completedBuildId) throws GetArtifactsException;

  /**
   * Retrieves test results, parses them and returns the corresponding {@link BlazeTestResults}. May
   * only be called once, after the build is complete.
   */
  BlazeTestResults getTestResults(Optional<String> completedBuildId) throws GetArtifactsException;

  /** Deletes the local BEP output file associated with the test results */
  default void deleteTemporaryOutputFiles() {}

  /**
   * Parses the BEP output data to collect all build flags used. Return all flags that pass filters
   */
  BuildFlags getBlazeFlags(Optional<String> completedBuildId) throws GetFlagsException;

  /**
   * Parses the BEP output data to collect message on stdout.
   *
   * <p>This function is designed for remote build which does not have local console output. Local
   * build should not use this since {@link ExternalTask} provide stdout handler.
   *
   * @param completedBuildId build id.
   * @param stderrConsumer process stderr
   * @param blazeContext blaze context may contains logging scope
   * @return a list of message on stdout.
   */
  default InputStream getStdout(
      String completedBuildId, Consumer<String> stderrConsumer, BlazeContext blazeContext)
      throws BuildException {
    return InputStream.nullInputStream();
  }

  /**
   * Parses the BEP output data to collect message on stderr.
   *
   * <p>This function is designed for remote build which does not have local console output. Local
   * build should not use this since {@link ExternalTask} provide stderr handler.
   *
   * @param completedBuildId build id.
   * @return a list of message on stderr.
   */
  default InputStream getStderr(String completedBuildId) throws BuildException {
    return InputStream.nullInputStream();
  }

  /**
   * Parses the BEP output data to collect all build flags used. Return all flags that pass filters
   */
  default BuildFlags getBlazeFlags() throws GetFlagsException {
    return getBlazeFlags(Optional.empty());
  }

  /**
   * Returns the build result. May only be called once, after the build is complete, or no artifacts
   * will be returned.
   *
   * @return The build artifacts from the build operation.
   */
  default ImmutableList<OutputArtifact> getAllOutputArtifacts(Predicate<String> pathFilter)
      throws GetArtifactsException {
    return getBuildOutput().getAllOutputArtifacts(pathFilter).asList();
  }

  /**
   * Returns the build artifacts, filtering out all artifacts not directly produced by the specified
   * target.
   *
   * <p>May only be called once, after the build is complete, or no artifacts will be returned.
   */
  default ImmutableList<OutputArtifact> getBuildArtifactsForTarget(
      Label target, Predicate<String> pathFilter) throws GetArtifactsException {
    return getBuildOutput().getDirectArtifactsForTarget(target, pathFilter).asList();
  }

  /**
   * Returns all build artifacts belonging to the given output groups. May only be called once,
   * after the build is complete, or no artifacts will be returned.
   */
  default ImmutableList<OutputArtifact> getArtifactsForOutputGroup(
      String outputGroup, Predicate<String> pathFilter) throws GetArtifactsException {
    return getBuildOutput().getOutputGroupArtifacts(outputGroup, pathFilter);
  }

  @Override
  void close();

  /** Indicates a failure to get artifact information */
  class GetArtifactsException extends BuildException {
    public GetArtifactsException(Throwable cause) {
      super(cause);
    }

    public GetArtifactsException(String message) {
      super(message);
    }

    public GetArtifactsException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /** Indicates a failure to get artifact information */
  class GetFlagsException extends Exception {
    public GetFlagsException(String message, Throwable cause) {
      super(message, cause);
    }

    public GetFlagsException(String message) {
      super(message);
    }

    public GetFlagsException(Throwable cause) {
      super(cause);
    }
  }
}
