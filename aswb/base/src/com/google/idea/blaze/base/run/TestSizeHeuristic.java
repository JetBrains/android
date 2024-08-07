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
package com.google.idea.blaze.base.run;

import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.dependencies.TestSize;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import java.io.File;
import javax.annotation.Nullable;

/** Matches source files to test rules based on size annotations/tags. */
public class TestSizeHeuristic implements TestTargetHeuristic {

  @Override
  public boolean matchesSource(
      Project project,
      TargetInfo target,
      @Nullable PsiFile sourcePsiFile,
      File sourceFile,
      @Nullable TestSize testSize) {
    // If testSize == null then prefer small
    // Some test runners will assume no size annotation == small and filter on that, others will not
    TestSize size = testSize != null ? testSize : TestSize.DEFAULT_NON_ANNOTATED_TEST_SIZE;
    return target.testSize == size;
  }
}
