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
package com.google.idea.blaze.java.syncstatus;

import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.syncstatus.SyncStatusContributor;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.impl.nodes.ClassTreeNode;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import javax.annotation.Nullable;

class JavaSyncStatusContributor implements SyncStatusContributor {

  @Nullable
  @Override
  public PsiFileAndName toPsiFileAndName(ProjectViewNode<?> node) {
    if (!(node instanceof ClassTreeNode)) {
      return null;
    }
    PsiClass psiClass = ((ClassTreeNode) node).getPsiClass();
    if (psiClass == null) {
      return null;
    }
    PsiFile file = psiClass.getContainingFile();
    return file != null ? new PsiFileAndName(file, psiClass.getName()) : null;
  }

  @Nullable
  @Override
  public PsiFileAndName toPsiFileAndName(BlazeProjectData projectData, ProjectViewNode<?> node) {
    return toPsiFileAndName(node);
  }

  @Override
  public boolean handlesFile(BlazeProjectData projectData, VirtualFile file) {
    return projectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.JAVA)
        && file.getName().endsWith(".java");
  }
}
