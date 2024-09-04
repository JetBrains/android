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
package com.google.idea.blaze.base.run.testlogs;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.common.artifact.BlazeArtifact;
import javax.annotation.Nullable;

/** The result of a single blaze test action. */
@AutoValue
public abstract class BlazeTestResult {

  /** The set of statuses for which no useful output XML is written. */
  public static final ImmutableSet<TestStatus> NO_USEFUL_OUTPUT =
      ImmutableSet.of(
          TestStatus.TIMEOUT,
          TestStatus.REMOTE_FAILURE,
          TestStatus.FAILED_TO_BUILD,
          TestStatus.TOOL_HALTED_BEFORE_TESTING);

  /** Status for a single blaze test action. */
  public enum TestStatus {
    NO_STATUS,
    PASSED,
    FLAKY,
    TIMEOUT,
    FAILED,
    INCOMPLETE,
    REMOTE_FAILURE,
    FAILED_TO_BUILD,
    TOOL_HALTED_BEFORE_TESTING,
  }

  public static BlazeTestResult create(
      Label label,
      @Nullable Kind targetKind,
      TestStatus testStatus,
      ImmutableSet<? extends BlazeArtifact> outputXmlFiles) {
    return new AutoValue_BlazeTestResult(label, targetKind, testStatus, outputXmlFiles);
  }

  public abstract Label getLabel();

  @Nullable
  public abstract Kind getTargetKind();

  public abstract TestStatus getTestStatus();

  public abstract ImmutableSet<? extends BlazeArtifact> getOutputXmlFiles();
}
