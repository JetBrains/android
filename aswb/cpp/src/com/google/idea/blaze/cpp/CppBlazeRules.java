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
package com.google.idea.blaze.cpp;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.RuleType;
import java.util.Arrays;
import java.util.function.Function;

/** C++-specific blaze rules handled by the plugin. */
public class CppBlazeRules implements Kind.Provider {

  /** C++-specific blaze rule types. */
  public enum RuleTypes {
    CC_LIBRARY("cc_library", LanguageClass.C, RuleType.LIBRARY),
    CC_BINARY("cc_binary", LanguageClass.C, RuleType.BINARY),
    CC_TEST("cc_test", LanguageClass.C, RuleType.TEST),
    CC_INC_LIBRARY("cc_inc_library", LanguageClass.C, RuleType.LIBRARY),
    CC_TOOLCHAIN("cc_toolchain", LanguageClass.C, RuleType.UNKNOWN),
    CC_TOOLCHAIN_ALIAS("cc_toolchain_alias", LanguageClass.C, RuleType.UNKNOWN),
    CC_TOOLCHAIN_SUITE("cc_toolchain_suite", LanguageClass.C, RuleType.UNKNOWN);

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

  @Override
  public Function<TargetIdeInfo, Kind> getTargetKindHeuristics() {
    return t -> null;
  }
}
