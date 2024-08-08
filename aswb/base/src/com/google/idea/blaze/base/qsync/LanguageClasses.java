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
package com.google.idea.blaze.base.qsync;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.qsync.project.QuerySyncLanguage;
import java.util.Collection;
import java.util.Optional;

/** A utility class to translate between language related enums. */
public class LanguageClasses {

  static final ImmutableBiMap<QuerySyncLanguage, LanguageClass>
      QUERY_SYNC_TO_BASE_LANGUAGE_CLASS_MAP =
          ImmutableBiMap.of(
              QuerySyncLanguage.JAVA, LanguageClass.JAVA,
              QuerySyncLanguage.KOTLIN, LanguageClass.KOTLIN,
              QuerySyncLanguage.CC, LanguageClass.C);

  private LanguageClasses() {}

  /**
   * Translates a set of {@link LanguageClass} values to a set of {@link QuerySyncLanguage} values
   * retaining only values meaningful in the query sync context.
   */
  static ImmutableSet<QuerySyncLanguage> toQuerySync(Collection<LanguageClass> from) {
    return from.stream().flatMap(it -> toQuerySync(it).stream()).collect(toImmutableSet());
  }

  /**
   * Translates a value of {@link LanguageClass} to a value of {@link QuerySyncLanguage}, if it has
   * a meaning in the query sync context.
   */
  static Optional<QuerySyncLanguage> toQuerySync(LanguageClass from) {
    return Optional.ofNullable(QUERY_SYNC_TO_BASE_LANGUAGE_CLASS_MAP.inverse().get(from));
  }

  static LanguageClass fromQuerySync(QuerySyncLanguage from) {
    return QUERY_SYNC_TO_BASE_LANGUAGE_CLASS_MAP.get(from);
  }

  static ImmutableSet<LanguageClass> fromQuerySync(Collection<QuerySyncLanguage> from) {
    return from.stream().map(LanguageClasses::fromQuerySync).collect(toImmutableSet());
  }
}
