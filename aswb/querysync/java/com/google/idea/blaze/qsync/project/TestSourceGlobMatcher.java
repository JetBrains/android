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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.nio.file.Path;

/**
 * Utility for matching test source glob expressions from a blazeproject file against project
 * folders
 */
public class TestSourceGlobMatcher {

  private final ImmutableSet<String> testSourceGlobs;

  public static TestSourceGlobMatcher create(ProjectDefinition projectDefinition) {
    return new TestSourceGlobMatcher(projectDefinition.testSources());
  }

  @VisibleForTesting
  public TestSourceGlobMatcher(ImmutableSet<String> testSources) {
    this.testSourceGlobs =
        testSources.stream().map(TestSourceGlobMatcher::modifyPattern).collect(toImmutableSet());
  }

  /** Returns true if any test source glob matches {@code path} */
  public boolean matches(Path path) {
    return testSourceGlobs.stream().anyMatch(glob -> path.toString().startsWith(glob));
  }

  /**
   * We modify the glob patterns provided by the user, so that their behavior more closely matches
   * what is expected. See {@link SourceTestConfig#modifyPattern}
   *
   * <p>Rules:
   * <li>path/ => path
   * <li>path/* => path
   * <li>path* => path
   */
  static String modifyPattern(String pattern) {
    if (pattern.endsWith("*")) {
      pattern = pattern.substring(0, pattern.length() - 1);
    }
    if (pattern.endsWith(File.separator)) {
      pattern = pattern.substring(0, pattern.length() - 1);
    }
    return pattern;
  }
}
