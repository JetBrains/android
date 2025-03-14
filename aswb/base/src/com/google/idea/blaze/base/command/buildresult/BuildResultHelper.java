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

import com.google.errorprone.annotations.MustBeClosed;
import com.google.idea.blaze.base.command.buildresult.bepparser.BuildEventStreamProvider;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.exception.BuildException;
import com.intellij.openapi.diagnostic.Logger;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** Assists in getting build artifacts from a build operation. */
public interface BuildResultHelper extends AutoCloseable {
  static final Logger logger = Logger.getInstance(BuildResultHelper.class);

  /**
   * Returns the build flags necessary for the build result helper to work.
   *
   * <p>The user must add these flags to their build command.
   */
  List<String> getBuildFlags();


  /**
   * Gets the BEP stream for the build. May only be called once. May only be called after the build is complete.
   */
  @MustBeClosed
  BuildEventStreamProvider getBepStream(Optional<String> completionBuildId) throws GetArtifactsException;

  /** Deletes the local BEP output file associated with the test results */
  default void deleteTemporaryOutputFiles() {}

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

  @Override
  void close();
}
