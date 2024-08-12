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
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.RuleType;
import java.util.Arrays;
import java.util.Collection;

/**
 * Android-specific blaze rules. This class is in the java module because android support is
 * provided for both IntelliJ and Android Studio.
 */
public final class AndroidBlazeRules implements Kind.Provider {

  /** Android-specific blaze rules. */
  public enum RuleTypes {
    ANDROID_BINARY("android_binary", LanguageClass.ANDROID, RuleType.BINARY),
    ANDROID_LIBRARY("android_library", LanguageClass.ANDROID, RuleType.LIBRARY),
    KT_ANDROID_LIBRARY("kt_android_library", LanguageClass.ANDROID, RuleType.LIBRARY),
    ANDROID_TEST("android_test", LanguageClass.ANDROID, RuleType.TEST),
    ANDROID_ROBOLECTRIC_TEST("android_robolectric_test", LanguageClass.ANDROID, RuleType.TEST),
    ANDROID_LOCAL_TEST("android_local_test", LanguageClass.ANDROID, RuleType.TEST),
    KT_ANDROID_LOCAL_TEST("kt_android_local_test", LanguageClass.ANDROID, RuleType.TEST),
    ANDROID_INSTRUMENTATION_TEST(
        "android_instrumentation_test", LanguageClass.ANDROID, RuleType.TEST),
    ANDROID_SDK("android_sdk", LanguageClass.ANDROID, RuleType.UNKNOWN),
    AAR_IMPORT("aar_import", LanguageClass.ANDROID, RuleType.UNKNOWN),
    ANDROID_RESOURCES("android_resources", LanguageClass.ANDROID, RuleType.UNKNOWN),
    KT_ANDROID_LIBRARY_HELPER(
        "kt_android_library_helper",
        ImmutableSet.of(LanguageClass.ANDROID, LanguageClass.KOTLIN),
        RuleType.LIBRARY),
    ;

    private final String name;
    private final ImmutableSet<LanguageClass> languageClasses;
    private final RuleType ruleType;

    RuleTypes(String name, Collection<LanguageClass> languageClasses, RuleType ruleType) {
      this.name = name;
      this.languageClasses = ImmutableSet.copyOf(languageClasses);
      this.ruleType = ruleType;
    }

    RuleTypes(String name, LanguageClass languageClass, RuleType ruleType) {
      this(name, ImmutableSet.of(languageClass), ruleType);
    }

    public Kind getKind() {
      return Preconditions.checkNotNull(Kind.fromRuleName(name));
    }
  }

  @Override
  public ImmutableSet<Kind> getTargetKinds() {
    return Arrays.stream(RuleTypes.values())
        .map(e -> Kind.Provider.create(e.name, e.languageClasses, e.ruleType))
        .collect(toImmutableSet());
  }
}
