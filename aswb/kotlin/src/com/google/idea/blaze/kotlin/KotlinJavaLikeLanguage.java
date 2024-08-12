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

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.java.sync.source.JavaLikeLanguage;
import com.google.idea.blaze.kotlin.KotlinBlazeRules.RuleTypes;

/** Kotlin-specific implementation of {@link JavaLikeLanguage}. */
class KotlinJavaLikeLanguage implements JavaLikeLanguage {
  @Override
  public ImmutableSet<String> getFileExtensions() {
    return ImmutableSet.of(".kt");
  }

  @Override
  public ImmutableSet<Kind> getDebuggableKinds() {
    return Kind.getKindsForLanguage(LanguageClass.KOTLIN).stream()
        .filter(
            k -> k.getRuleType().equals(RuleType.TEST) || k.getRuleType().equals(RuleType.BINARY))
        .collect(toImmutableSet());
  }

  @Override
  public ImmutableSet<Kind> getHandledTestKinds() {
    return Kind.getKindsForLanguage(LanguageClass.KOTLIN).stream()
        .filter(k -> k.getRuleType().equals(RuleType.TEST))
        .collect(toImmutableSet());
  }

  @Override
  public ImmutableSet<Kind> getNonSourceKinds() {
    return ImmutableSet.of(RuleTypes.KT_JVM_IMPORT.getKind(), RuleTypes.KOTLIN_STDLIB.getKind());
  }
}
