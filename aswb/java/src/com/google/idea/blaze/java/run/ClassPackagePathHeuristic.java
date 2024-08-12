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
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import java.io.File;
import javax.annotation.Nullable;

/**
 * Matches test targets to source files based on whether or not the file path corresponding to the
 * full qualified class name is contained in the target name.
 *
 * <p>E.g. The path for this class is com/google/idea/blaze/java/run/ClassPackagePathHeuristic
 */
public class ClassPackagePathHeuristic implements TestTargetHeuristic {

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
    String targetName = target.label.targetName().toString();
    if (!targetName.contains("/")) {
      return false;
    }
    return ReadAction.compute(() -> doMatchesSource((PsiClassOwner) sourcePsiFile, targetName));
  }

  private static boolean doMatchesSource(PsiClassOwner source, String targetName) {
    for (PsiClass psiClass : source.getClasses()) {
      String qualifiedName = psiClass.getQualifiedName();
      if (qualifiedName == null) {
        continue;
      }
      String classPackagePath = psiClass.getQualifiedName().replace('.', '/');
      if (targetName.contains(classPackagePath)) {
        return true;
      }
    }
    return false;
  }
}
