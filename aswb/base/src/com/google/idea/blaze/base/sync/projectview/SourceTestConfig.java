/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.projectview;

import com.google.common.annotations.VisibleForTesting;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.Glob;
import com.google.idea.blaze.base.projectview.section.sections.TestSourceSection;
import com.intellij.openapi.util.text.StringUtil;
import java.io.File;
import java.util.stream.Collectors;

/** Affects the way sources are imported. */
public class SourceTestConfig {
  private final Glob.GlobSet testSources;

  public SourceTestConfig(ProjectViewSet projectViewSet) {
    this.testSources =
        new Glob.GlobSet(
            projectViewSet
                .listItems(TestSourceSection.KEY)
                .stream()
                .map(SourceTestConfig::modifyGlob)
                .collect(Collectors.toList()));
  }

  private static Glob modifyGlob(Glob glob) {
    return new Glob(modifyPattern(glob.toString()));
  }

  /**
   * We modify the glob patterns provided by the user, so that their behavior more closely matches
   * what is expected.
   *
   * <p>Rules:
   * <li>path/ => path*
   * <li>path/* => path*
   * <li>path => path*
   */
  @VisibleForTesting
  static String modifyPattern(String pattern) {
    pattern = StringUtil.trimEnd(pattern, '*');
    pattern = StringUtil.trimEnd(pattern, File.separatorChar);
    return pattern + "*";
  }

  /** Returns true if this artifact is a test artifact. */
  public boolean isTestSource(String relativePath) {
    return testSources.matches(relativePath);
  }
}
