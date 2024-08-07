/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.model.primitives;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import java.util.Arrays;

/** Contributes generic rules to {@link Kind}. */
public final class GenericBlazeRules implements Kind.Provider {

  /** Generic blaze rule types. */
  public enum RuleTypes {
    PROTO_LIBRARY("proto_library", LanguageClass.GENERIC, RuleType.LIBRARY),
    TEST_SUITE("test_suite", LanguageClass.GENERIC, RuleType.TEST),
    INTELLIJ_PLUGIN_DEBUG_TARGET(
        "intellij_plugin_debug_target", LanguageClass.JAVA, RuleType.UNKNOWN),
    WEB_TEST("web_test", LanguageClass.GENERIC, RuleType.TEST),
    SH_TEST("sh_test", LanguageClass.GENERIC, RuleType.TEST),
    SH_LIBRARY("sh_library", LanguageClass.GENERIC, RuleType.LIBRARY),
    SH_BINARY("sh_binary", LanguageClass.GENERIC, RuleType.BINARY);

    private final String name;
    private final LanguageClass languageClass;
    private final RuleType ruleType;

    RuleTypes(String name, LanguageClass languageClass, RuleType ruleType) {
      this.name = name;
      this.languageClass = languageClass;
      this.ruleType = ruleType;
    }

    public Kind getKind() {
      return Preconditions.checkNotNull(Kind.fromRuleName(name));
    }
  }

  @Override
  public ImmutableSet<Kind> getTargetKinds() {
    return Arrays.stream(RuleTypes.values())
        .map(e -> Kind.Provider.create(e.name, e.languageClass, e.ruleType))
        .collect(toImmutableSet());
  }
}
