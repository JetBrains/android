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
package com.google.idea.blaze.base.run;

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.dependencies.TestSize;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import java.io.File;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * For cases where the target's size attribute isn't known, try to guess the size from portions of
 * the target name. This is a very rough heuristic, and should be run near the end, after more
 * precise heuristics.
 */
public class TestSizeFromRoughTargetNameHeuristic implements TestTargetHeuristic {

  private static final ImmutableMap<String, TestSize> TARGET_NAME_TO_TEST_SIZE =
      ImmutableMap.of(
          "small", TestSize.SMALL,
          "medium", TestSize.MEDIUM,
          "large", TestSize.LARGE,
          "enormous", TestSize.ENORMOUS);

  /** Looks for an substring match between the rule name and the test size annotation class name. */
  @Nullable
  private static TestSize guessTargetTestSize(TargetInfo target) {
    String ruleName = target.label.targetName().toString().toLowerCase();
    return TARGET_NAME_TO_TEST_SIZE.entrySet().stream()
        .filter(entry -> ruleName.contains(entry.getKey()))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse(null);
  }

  @Override
  public boolean matchesSource(
      Project project,
      TargetInfo target,
      @Nullable PsiFile sourcePsiFile,
      File sourceFile,
      @Nullable TestSize testSize) {
    if (target.testSize != null) {
      return false; // no need to guess, we already know the target's size attribute
    }
    // if no size annotation is present, treat as small tests (b/33503928).
    if (testSize == null) {
      testSize = TestSize.SMALL;
    }
    return testSize == guessTargetTestSize(target);
  }
}
