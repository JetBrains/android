/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.project;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.qsync.project.LanguageClassProto.LanguageClass;
import java.util.Collection;
import java.util.Optional;

/** A language class that the query sync supports/needs to care about. */
public enum QuerySyncLanguage {
  JAVA(LanguageClass.LANGUAGE_CLASS_JAVA, DependencyTrackingBehavior.EXTERNAL_DEPENDENCIES),
  KOTLIN(LanguageClass.LANGUAGE_CLASS_KOTLIN, DependencyTrackingBehavior.EXTERNAL_DEPENDENCIES),
  CC(LanguageClass.LANGUAGE_CLASS_CC, DependencyTrackingBehavior.SELF);

  QuerySyncLanguage(
      LanguageClassProto.LanguageClass protoValue,
      DependencyTrackingBehavior dependencyTrackingBehavior) {
    this.protoValue = protoValue;
    this.dependencyTrackingBehavior = dependencyTrackingBehavior;
  }

  public final LanguageClassProto.LanguageClass protoValue;
  public final DependencyTrackingBehavior dependencyTrackingBehavior;

  public static Optional<QuerySyncLanguage> fromProto(LanguageClassProto.LanguageClass proto) {
    for (QuerySyncLanguage lang : values()) {
      if (lang.protoValue == proto) {
        return Optional.of(lang);
      }
    }
    return Optional.empty();
  }

  public static ImmutableSet<QuerySyncLanguage> fromProtoList(Collection<LanguageClass> list) {
    return list.stream()
        .map(QuerySyncLanguage::fromProto)
        .flatMap(Optional::stream)
        .collect(toImmutableSet());
  }
}
