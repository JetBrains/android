/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
import com.google.idea.blaze.java.run.producers.BlazeJUnitTestFilterFlags.JUnitVersion;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import java.io.File;
import javax.annotation.Nullable;

/** Matches junit test sources to test targets with junit3/junit4 in their name. */
public class JUnitTestHeuristic implements TestTargetHeuristic {

  @Override
  public boolean matchesSource(
      Project project,
      TargetInfo target,
      @Nullable PsiFile sourcePsiFile,
      File sourceFile,
      @Nullable TestSize testSize) {
    JUnitVersion sourceVersion = junitVersion(sourcePsiFile);
    if (sourceVersion == null) {
      return false;
    }
    String targetName = target.label.targetName().toString().toLowerCase();
    switch (sourceVersion) {
      case JUNIT_4:
        return targetName.contains("junit4");
      case JUNIT_3:
        return targetName.contains("junit3");
    }
    return false;
  }

  @Nullable
  private JUnitVersion junitVersion(@Nullable PsiFile psiFile) {
    if (!(psiFile instanceof PsiClassOwner)) {
      return null;
    }
    return ReadAction.compute(() -> junitVersion((PsiClassOwner) psiFile));
  }

  @Nullable
  private JUnitVersion junitVersion(PsiClassOwner classOwner) {
    for (PsiClass psiClass : classOwner.getClasses()) {
      if (JUnitUtil.isJUnit4TestClass(psiClass)) {
        return JUnitVersion.JUNIT_4;
      }
    }
    for (PsiClass psiClass : classOwner.getClasses()) {
      if (JUnitUtil.isJUnit3TestClass(psiClass)) {
        return JUnitVersion.JUNIT_3;
      }
    }
    return null;
  }
}
