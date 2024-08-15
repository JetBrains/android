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
package com.google.idea.blaze.python.sync;

import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.syncstatus.SyncStatusContributor;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.PyFile;
import javax.annotation.Nullable;

class PySyncStatusContributor implements SyncStatusContributor {

  @Nullable
  @Override
  public PsiFileAndName toPsiFileAndName(BlazeProjectData projectData, ProjectViewNode<?> node) {
    if (!(node instanceof PsiFileNode)) {
      return null;
    }
    PsiFile psiFile = ((PsiFileNode) node).getValue();
    if (!(psiFile instanceof PyFile)) {
      return null;
    }
    return new PsiFileAndName(psiFile, psiFile.getName());
  }

  @Override
  public boolean handlesFile(BlazeProjectData projectData, VirtualFile file) {
    return projectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.PYTHON)
        && file.getName().endsWith(".py");
  }
}
