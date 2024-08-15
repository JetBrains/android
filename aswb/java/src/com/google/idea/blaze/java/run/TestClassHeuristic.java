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
package com.google.idea.blaze.java.run;

import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.dependencies.TestSize;
import com.google.idea.blaze.base.run.TestTargetHeuristic;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import java.io.File;
import java.util.Arrays;
import javax.annotation.Nullable;

/** Matches test targets to source files based on the 'test_class' target attribute. */
public class TestClassHeuristic implements TestTargetHeuristic {

  @Override
  public boolean matchesSource(
      Project project,
      TargetInfo target,
      @Nullable PsiFile sourcePsiFile,
      File sourceFile,
      @Nullable TestSize testSize) {
    if (!(sourcePsiFile instanceof PsiClassOwner)) {
      return false;
    }
    if (target.testClass == null) {
      return false;
    }
    return ReadAction.compute(
        () -> doMatchesSource((PsiClassOwner) sourcePsiFile, target.testClass));
  }

  private static boolean doMatchesSource(PsiClassOwner source, String testClass) {
    return Arrays.stream(source.getClasses()).anyMatch(c -> testClass.equals(c.getQualifiedName()));
  }
}
