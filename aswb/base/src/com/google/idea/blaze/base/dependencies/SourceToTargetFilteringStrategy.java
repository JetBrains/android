/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.sync.projectview.LanguageSupport;
import com.intellij.openapi.extensions.ExtensionPointName;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Used by {@link SourceToTargetProvider} to map source files to blaze targets which should be
 * synced to resolve those source files. Each instance optionally filters and reorders the list of
 * targets returned by {@link SourceToTargetProvider}.
 */
public interface SourceToTargetFilteringStrategy {

  ExtensionPointName<SourceToTargetFilteringStrategy> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.SourceToTargetFilteringStrategy");

  static ImmutableList<TargetInfo> filterTargets(List<TargetInfo> targets) {
    Stream<TargetInfo> stream = targets.stream();
    for (SourceToTargetFilteringStrategy strategy : EP_NAME.getExtensions()) {
      stream = strategy.filter(stream);
    }
    return stream.collect(toImmutableList());
  }

  Stream<TargetInfo> filter(Stream<TargetInfo> targets);

  /** Ignores rules which aren't relevant to resolving source code. */
  class IgnoredRules implements SourceToTargetFilteringStrategy {

    private static final ImmutableSet<String> IGNORED_RULE_KINDS = ImmutableSet.of("filegroup");

    @Override
    public Stream<TargetInfo> filter(Stream<TargetInfo> targets) {
      return targets.filter(info -> !IGNORED_RULE_KINDS.contains(info.kindString));
    }
  }

  /** Ignores rules of a known language which isn't supported by the current IDE. */
  class SupportedLanguages implements SourceToTargetFilteringStrategy {
    @Override
    public Stream<TargetInfo> filter(Stream<TargetInfo> targets) {
      return targets.filter(SupportedLanguages::supportedLanguage);
    }

    private static boolean supportedLanguage(TargetInfo target) {
      Kind kind = target.getKind();
      return kind != null
          && !Collections.disjoint(
              LanguageSupport.languagesSupportedByCurrentIde(), kind.getLanguageClasses());
    }
  }

  /** Orders rules based on {@link Kind} and {@link RuleType}. */
  class PrioritizeKnownRules implements SourceToTargetFilteringStrategy {

    @Override
    public Stream<TargetInfo> filter(Stream<TargetInfo> targets) {
      return targets.sorted(Comparator.comparingInt(PrioritizeKnownRules::weight));
    }

    private static int weight(TargetInfo info) {
      Kind aKind = info.getKind();
      return aKind != null ? weight(aKind.getRuleType()) : 100;
    }

    /** Order of priority: library, test/binary, unknown. */
    private static int weight(RuleType type) {
      switch (type) {
        case LIBRARY:
          return 0;
        case BINARY:
        case TEST:
          return 1;
        case UNKNOWN:
          return 2;
      }
      throw new RuntimeException("Unhandled RuleType: " + type);
    }
  }
}
