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

import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.common.BuildTarget;
import java.time.Instant;
import javax.annotation.Nullable;

/**
 * Some minimal data about a blaze target. This is intended to contain the data common to our aspect
 * output, and the per-target data provided by a global dependency index.
 */
public record TargetInfo(
    Label label,
    String kindString,
    @Nullable TestSize testSize,
    @Nullable String testClass,
    @Nullable Instant syncTime) {

  // Compact constructor to provide defaults for the optional fields, matching the old two-argument
  // constructor.
  public TargetInfo(Label label, String kindString) {
    this(label, kindString, null, null, null);
  }

  @Nullable
  public Kind getKind() {
    return Kind.fromRuleName(kindString);
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

  public static TargetInfo fromBuildTarget(BuildTarget buildTarget) {
    return new TargetInfo(Label.create(buildTarget.label().toString()), buildTarget.kind());
  }
}