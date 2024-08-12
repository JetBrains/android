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
package com.google.idea.blaze.python.run.producers;

import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.run.SourceToTargetFinder;
import com.google.idea.blaze.base.run.producers.BinaryContextProvider;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.PyFile;
import java.io.File;
import java.util.Optional;
import javax.annotation.Nullable;

/** Producer for run configurations related to py_binary main classes in Blaze. */
class PyBinaryContextProvider implements BinaryContextProvider {

  @Nullable
  @Override
  public BinaryRunContext getRunContext(ConfigurationContext context) {
    Location<?> location = context.getLocation();
    if (location == null) {
      return null;
    }
    PsiElement element = location.getPsiElement();
    PsiFile file = element.getContainingFile();
    if (!(file instanceof PyFile)) {
      return null;
    }
    TargetInfo binaryTarget = getTargetLabel(file);
    if (binaryTarget == null) {
      return null;
    }
    return BinaryRunContext.create(file, binaryTarget);
  }

  @Nullable
  private static TargetInfo getTargetLabel(PsiFile psiFile) {
    VirtualFile vf = psiFile.getVirtualFile();
    if (vf == null) {
      return null;
    }
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(psiFile.getProject()).getBlazeProjectData();
    if (projectData == null) {
      return null;
    }
    File file = new File(vf.getPath());
    String fileName = FileUtil.getNameWithoutExtension(file);
    return SourceToTargetFinder.findTargetsForSourceFile(
            psiFile.getProject(), file, Optional.of(RuleType.BINARY))
        .stream()
        .filter(t -> acceptTarget(fileName, t))
        .findFirst()
        .orElse(null);
  }

  private static boolean acceptTarget(String fileName, TargetInfo target) {
    Kind kind = target.getKind();
    if (kind == null
        || !kind.hasLanguage(LanguageClass.PYTHON)
        || !kind.getRuleType().equals(RuleType.BINARY)) {
      return false;
    }
    // The 'main' attribute isn't exposed, so only suggest a binary if the name matches
    return target.label.targetName().toString().equals(fileName);
  }
}
