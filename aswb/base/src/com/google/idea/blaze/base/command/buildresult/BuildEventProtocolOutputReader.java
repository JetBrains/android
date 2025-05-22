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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos;
import com.google.idea.blaze.base.command.buildresult.bepparser.BuildEventStreamProvider;
import com.google.idea.blaze.base.command.buildresult.bepparser.BuildEventStreamProvider.BuildEventStreamException;
import com.google.idea.blaze.base.command.buildresult.bepparser.OutputArtifactParser;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResult;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResult.TestStatus;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResults;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/** Utility methods for reading Blaze's build event procotol output, in proto form. */
public final class BuildEventProtocolOutputReader {

  private BuildEventProtocolOutputReader() {}
  /**
   * Returns all test results from {@link BuildEventStreamProvider}.
   *
   * @throws BuildEventStreamException if the BEP {@link BuildEventStreamProvider} is incorrectly
   *     formatted
   */
  public static BlazeTestResults parseTestResults(BuildEventStreamProvider streamProvider)
      throws BuildEventStreamException {
    Map<String, Kind> labelToKind = new HashMap<>();
    long startTimeMillis = 0L;
    ImmutableList.Builder<BlazeTestResult> results = ImmutableList.builder();
    BuildEventStreamProtos.BuildEvent event;
    while ((event = streamProvider.getNext()) != null) {
      switch (event.getId().getIdCase()) {
        case STARTED:
          startTimeMillis = event.getStarted().getStartTimeMillis();
          continue;
        case TARGET_COMPLETED:
          String label = event.getId().getTargetCompleted().getLabel();
          Kind kind = parseTargetKind(event.getCompleted().getTargetKind());
          if (kind != null) {
            labelToKind.put(label, kind);
          }
          continue;
        case TARGET_CONFIGURED:
          label = event.getId().getTargetConfigured().getLabel();
          kind = parseTargetKind(event.getConfigured().getTargetKind());
          if (kind != null) {
            labelToKind.put(label, kind);
          }
          continue;
        case TEST_RESULT:
          label = event.getId().getTestResult().getLabel();
          results.add(
              parseTestResult(
                  label,
                  labelToKind.get(label),
                  event.getTestResult(),
                  startTimeMillis));
          continue;
        case ACTION_COMPLETED:
          label = event.getId().getActionCompleted().getLabel();
          // If no test result is available after action_completed event,
          // add a NO_STATUS test result.
          if (results.build().isEmpty()) {
            results.add(
              parseTestResult(
                label,
                labelToKind.get(label),
                event.getTestResult(),
                startTimeMillis));
          }
          continue;
        default: // continue
      }
    }
    return BlazeTestResults.fromFlatList(results.build());
  }

  /** Convert BEP 'target_kind' to our internal format */
  @Nullable
  private static Kind parseTargetKind(String kind) {
    return kind.endsWith(" rule")
        ? Kind.fromRuleName(kind.substring(0, kind.length() - " rule".length()))
        : null;
  }

  private static BlazeTestResult parseTestResult(
      String label,
      @Nullable Kind kind,
      BuildEventStreamProtos.TestResult testResult,
      long startTimeMillis) {
    ImmutableSet<OutputArtifact> files =
        testResult.getTestActionOutputList().stream()
            .map(
                file ->
                    parseTestFile(file, path -> path.endsWith(".xml"), startTimeMillis))
            .filter(Objects::nonNull)
            .collect(toImmutableSet());
    return BlazeTestResult.create(
        Label.create(label), kind, convertTestStatus(testResult.getStatus()), files);
  }

  private static TestStatus convertTestStatus(BuildEventStreamProtos.TestStatus protoStatus) {
    if (protoStatus == BuildEventStreamProtos.TestStatus.UNRECOGNIZED) {
      // for forward-compatibility
      return TestStatus.NO_STATUS;
    }
    return TestStatus.valueOf(protoStatus.name());
  }

  @Nullable
  private static OutputArtifact parseTestFile(
      BuildEventStreamProtos.File file,
      Predicate<String> fileFilter,
      long startTimeMillis) {
    OutputArtifact output = OutputArtifactParser.parseArtifact(file, startTimeMillis);
    return output == null || !fileFilter.test(output.getBazelOutRelativePath()) ? null : output;
  }
}
