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
package com.google.idea.blaze.kotlin;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.RuleType;
import java.util.Arrays;
import java.util.function.Function;

/** Contributes kotlin rules to {@link Kind}. */
public final class KotlinBlazeRules implements Kind.Provider {

  /** Kotlin-specific blaze rule types. */
  public enum RuleTypes {
    KT_JVM_TOOLCHAIN("kt_jvm_toolchain", LanguageClass.KOTLIN, RuleType.UNKNOWN),
    // TODO(b/157683101): remove once https://youtrack.jetbrains.com/issue/KT-24309 is fixed
    KT_JVM_LIBRARY_HELPER("kt_jvm_library_helper", LanguageClass.KOTLIN, RuleType.LIBRARY),
    // bazel only kotlin rules:
    KT_JVM_LIBRARY("kt_jvm_library", LanguageClass.KOTLIN, RuleType.LIBRARY),
    KT_JVM_BINARY("kt_jvm_binary", LanguageClass.KOTLIN, RuleType.BINARY),
    KT_JVM_TEST("kt_jvm_test", LanguageClass.KOTLIN, RuleType.TEST),
    KT_JVM_IMPORT("kt_jvm_import", LanguageClass.KOTLIN, RuleType.UNKNOWN),
    KOTLIN_STDLIB("kotlin_stdlib", LanguageClass.KOTLIN, RuleType.UNKNOWN);

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
    return proto ->
        proto.getKindString().startsWith("kt_jvm_")
                || proto.getKindString().startsWith("kt_android_")
            ? Kind.Provider.create(proto.getKindString(), LanguageClass.KOTLIN, RuleType.UNKNOWN)
            : null;
  }
}
