/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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

import com.google.common.collect.Interner;
import com.google.idea.blaze.base.command.buildresult.bepparser.BepParser;
import com.google.idea.blaze.base.command.buildresult.bepparser.BuildEventStreamProvider;
import com.google.idea.blaze.base.command.buildresult.bepparser.ParsedBepOutput;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResults;
import com.intellij.openapi.diagnostic.Logger;

/**
 * A utility class that knows how to collect data from {@link BuildEventStreamProvider} in a use case specific way.
 */
public final class BuildResultParser {
  private BuildResultParser() { }

  /**
   * Parses the BEP stream and returns the corresponding {@link ParsedBepOutput}. May only be
   * called once on a given stream.
   *
   * <p>As BEP retrieval can be memory-intensive for large projects, implementations of
   * getBuildOutput may restrict parallelism for cases in which many builds are executed in parallel
   * (e.g. remote builds).
   */
  public static ParsedBepOutput getBuildOutput(
    BuildEventStreamProvider bepStream, Interner<String> stringInterner)
    throws GetArtifactsException {
    try {
      return BepParser.parseBepArtifacts(bepStream, stringInterner);
    }
    catch (BuildEventStreamProvider.BuildEventStreamException e) {
      Logger.getInstance(BuildResultParser.class).error(e);
      throw new GetArtifactsException(String.format(
        "Failed to parse bep for build id: %s: %s", bepStream.getId(), e.getMessage()));
    }
  }

  /**
   * Parses the BEP stream and returns the corresponding {@link ParsedBepOutput}. May only be
   * called once on a given stream.
   *
   * <p>As BEP retrieval can be memory-intensive for large projects, implementations of
   * getBuildOutput may restrict parallelism for cases in which many builds are executed in parallel
   * (e.g. remote builds).
   */
  public static ParsedBepOutput.Legacy getBuildOutputForLegacySync(
    BuildEventStreamProvider bepStream, Interner<String> stringInterner)
    throws GetArtifactsException {
    try {
      return BepParser.parseBepArtifactsForLegacySync(bepStream, stringInterner);
    }
    catch (BuildEventStreamProvider.BuildEventStreamException e) {
      Logger.getInstance(BuildResultParser.class).error(e);
      throw new GetArtifactsException(String.format(
        "Failed to parse bep for build id: %s: %s", bepStream.getId(), e.getMessage()));
    }
  }

  /**
   * Parses BEP stream and returns the corresponding {@link BlazeTestResults}. May
   * only be called once on a given stream.
   */
  public static BlazeTestResults getTestResults(BuildEventStreamProvider bepStream) throws GetArtifactsException {
    try {
      return BuildEventProtocolOutputReader.parseTestResults(bepStream);
    }
    catch (BuildEventStreamProvider.BuildEventStreamException e) {
      BuildResultHelper.logger.warn(e);
      throw new GetArtifactsException(
        String.format("Failed to parse bep for build id: %s", bepStream.getId()), e);
    }
  }
}
