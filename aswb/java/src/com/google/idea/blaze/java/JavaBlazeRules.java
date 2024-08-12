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
package com.google.idea.blaze.java;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.RuleType;
import java.util.Arrays;
import java.util.function.Function;

/** Contributes java rules to {@link Kind}. */
public final class JavaBlazeRules implements Kind.Provider {

  /** Java-specific blaze rules. */
  public enum RuleTypes {
    JAVA_LIBRARY("java_library", LanguageClass.JAVA, RuleType.LIBRARY),
    JAVA_TEST("java_test", LanguageClass.JAVA, RuleType.TEST),
    JAVA_BINARY("java_binary", LanguageClass.JAVA, RuleType.BINARY),
    JAVA_IMPORT("java_import", LanguageClass.JAVA, RuleType.UNKNOWN),
    JAVA_TOOLCHAIN("java_toolchain", LanguageClass.JAVA, RuleType.UNKNOWN),
    JAVA_PROTO_LIBRARY("java_proto_library", LanguageClass.JAVA, RuleType.LIBRARY),
    JAVA_LITE_PROTO_LIBRARY("java_lite_proto_library", LanguageClass.JAVA, RuleType.LIBRARY),
    JAVA_MUTABLE_PROTO_LIBRARY("java_mutable_proto_library", LanguageClass.JAVA, RuleType.LIBRARY),
    JAVA_PLUGIN("java_plugin", LanguageClass.JAVA, RuleType.UNKNOWN),
    JAVA_WRAP_CC("java_wrap_cc", LanguageClass.JAVA, RuleType.UNKNOWN),
    GWT_APPLICATION("gwt_application", LanguageClass.JAVA, RuleType.UNKNOWN),
    GWT_HOST("gwt_host", LanguageClass.JAVA, RuleType.UNKNOWN),
    GWT_MODULE("gwt_module", LanguageClass.JAVA, RuleType.UNKNOWN),
    GWT_TEST("gwt_test", LanguageClass.JAVA, RuleType.TEST),
    JAVA_WEB_TEST("java_web_test", LanguageClass.JAVA, RuleType.TEST);

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

  public static ImmutableSet<Kind> getJavaProtoLibraryKinds() {
    return ImmutableSet.of(
        RuleTypes.JAVA_PROTO_LIBRARY.getKind(),
        RuleTypes.JAVA_LITE_PROTO_LIBRARY.getKind(),
        RuleTypes.JAVA_MUTABLE_PROTO_LIBRARY.getKind());
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
        proto.hasJavaIdeInfo() || proto.hasJavaToolchainIdeInfo()
            ? Kind.Provider.create(proto.getKindString(), LanguageClass.JAVA, RuleType.UNKNOWN)
            : null;
  }
}
