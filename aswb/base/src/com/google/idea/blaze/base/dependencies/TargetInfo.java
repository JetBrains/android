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
package com.google.idea.blaze.base.dependencies;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.common.BuildTarget;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Some minimal data about a blaze target. This is intended to contain the data common to our aspect
 * output, and the per-target data provided by a global dependency index.
 */
public class TargetInfo {
  public final Label label;
  public final String kindString;
  @Nullable public final TestSize testSize;
  @Nullable public final String testClass;
  @Nullable public final Instant syncTime;
  @Nullable private final ImmutableList<ArtifactLocation> sources;

  private TargetInfo(
      Label label,
      String kindString,
      @Nullable TestSize testSize,
      @Nullable String testClass,
      @Nullable Instant syncTime,
      @Nullable ImmutableList<ArtifactLocation> sources) {
    this.label = label;
    this.kindString = kindString;
    this.testSize = testSize;
    this.testClass = testClass;
    this.syncTime = syncTime;
    this.sources = sources;
  }

  @Nullable
  public Kind getKind() {
    return Kind.fromRuleName(kindString);
  }

  public Label getLabel() {
    return label;
  }

  /** Returns this targets sources, or Optional#empty if they're not known. */
  public Optional<ImmutableList<ArtifactLocation>> getSources() {
    return Optional.ofNullable(sources);
  }

  public RuleType getRuleType() {
    Kind kind = getKind();
    if (kind != null && kind.getRuleType() != RuleType.UNKNOWN) {
      return kind.getRuleType();
    }
    return guessRuleTypeFromKindString(kindString);
  }

  /**
   * Guess rule type so that we have some handling of generic rules (e.g. rules providing
   * java_common that aren't otherwise recognized by name string).
   */
  private static RuleType guessRuleTypeFromKindString(String kindString) {
    if (kindString.endsWith("_test") || isTestSuite(kindString)) {
      return RuleType.TEST;
    }
    if (kindString.endsWith("_binary")) {
      return RuleType.BINARY;
    }
    if (kindString.endsWith("_library")) {
      return RuleType.LIBRARY;
    }
    return RuleType.UNKNOWN;
  }

  private static boolean isTestSuite(String ruleType) {
    return "test_suite".equals(ruleType);
  }

  @Override
  public String toString() {
    return String.format("%s (%s)", label, kindString);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TargetInfo)) {
      return false;
    }
    TargetInfo other = (TargetInfo) o;
    return label.equals(other.label) && kindString.equals(other.kindString);
  }

  @Override
  public int hashCode() {
    return Objects.hash(label, kindString);
  }

  public static Builder builder(Label label, String kindString) {
    return new Builder(label, kindString);
  }

  /** Builder class for {@link TargetInfo}. */
  public static class Builder {
    private final Label label;
    private final String kindString;
    @Nullable private TestSize testSize;
    @Nullable private String testClass;
    @Nullable private Instant syncTime;
    @Nullable private ImmutableList<ArtifactLocation> sources;

    private Builder(Label label, String kindString) {
      this.label = label;
      this.kindString = kindString;
    }

    @CanIgnoreReturnValue
    public Builder setTestSize(@Nullable TestSize testSize) {
      this.testSize = testSize;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setTestClass(@Nullable String testClass) {
      this.testClass = testClass;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setSyncTime(@Nullable Instant syncTime) {
      this.syncTime = syncTime;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setSources(ImmutableList<ArtifactLocation> sources) {
      this.sources = sources;
      return this;
    }

    public TargetInfo build() {
      return new TargetInfo(label, kindString, testSize, testClass, syncTime, sources);
    }
  }

  public static TargetInfo fromBuildTarget(BuildTarget buildTarget) {
    return TargetInfo.builder(Label.create(buildTarget.label().toString()), buildTarget.kind())
        .build();
  }
}
