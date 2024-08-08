/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.lang.buildfile.language.semantics;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.lib.query2.proto.proto2api.Build;
import com.google.devtools.build.lib.query2.proto.proto2api.Build.BuildLanguage;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import javax.annotation.Nullable;

/**
 * Specification of the BUILD language, as provided by "blaze info build-language".
 *
 * <p>This constitutes a set of rules, along with their supported attributes, and other useful
 * information. We query this once per blaze workspace (it won't change unless the blaze binary is
 * also changed).
 *
 * <p>This rule list is not exhaustive; it's intended to give information about known rules, not
 * enumerate all possibilities.
 */
public class BuildLanguageSpec implements ProtoWrapper<Build.BuildLanguage> {
  private final ImmutableMap<String, RuleDefinition> rules;

  @VisibleForTesting
  public BuildLanguageSpec(ImmutableMap<String, RuleDefinition> rules) {
    this.rules = rules;
  }

  public static BuildLanguageSpec fromProto(BuildLanguage proto) {
    return new BuildLanguageSpec(
        proto.getRuleList().stream()
            .map(RuleDefinition::fromProto)
            .collect(ImmutableMap.toImmutableMap(RuleDefinition::getName, Functions.identity())));
  }

  @Override
  public BuildLanguage toProto() {
    return Build.BuildLanguage.newBuilder()
        .addAllRule(ProtoWrapper.mapToProtos(rules.values()))
        .build();
  }

  public ImmutableMap<String, RuleDefinition> getRules() {
    return rules;
  }

  public ImmutableSet<String> getKnownRuleNames() {
    return getRules().keySet();
  }

  public boolean hasRule(@Nullable String ruleName) {
    return getRule(ruleName) != null;
  }

  @Nullable
  public RuleDefinition getRule(@Nullable String ruleName) {
    return ruleName != null ? getRules().get(ruleName) : null;
  }
}
