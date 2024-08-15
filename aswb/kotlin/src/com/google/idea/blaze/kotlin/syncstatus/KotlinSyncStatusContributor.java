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
package com.google.idea.blaze.kotlin.syncstatus;

import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.syncstatus.SyncStatusContributor;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.openapi.vfs.VirtualFile;
import javax.annotation.Nullable;
import org.jetbrains.kotlin.idea.projectView.KtClassOrObjectTreeNode;
import org.jetbrains.kotlin.idea.projectView.KtFileTreeNode;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.kotlin.psi.KtFile;

class KotlinSyncStatusContributor implements SyncStatusContributor {

  @Nullable
  @Override
  public PsiFileAndName toPsiFileAndName(BlazeProjectData projectData, ProjectViewNode<?> node) {
    if (!projectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.KOTLIN)) {
      return null;
    }
    return toPsiFileAndName(node);
  }

  @Nullable
  @Override
  public PsiFileAndName toPsiFileAndName(ProjectViewNode<?> node) {
    if (node instanceof KtFileTreeNode) {
      KtFile file = ((KtFileTreeNode) node).getKtFile();
      return new PsiFileAndName(file, file.getName());
    }
    if (node instanceof KtClassOrObjectTreeNode) {
      KtClassOrObject kt = ((KtClassOrObjectTreeNode) node).getValue();
      return kt != null ? new PsiFileAndName(kt.getContainingKtFile(), kt.getName()) : null;
    }
    return null;
  }

  @Override
  public boolean handlesFile(BlazeProjectData projectData, VirtualFile file) {
    return projectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.KOTLIN)
        && file.getName().endsWith(".kt");
  }
}
