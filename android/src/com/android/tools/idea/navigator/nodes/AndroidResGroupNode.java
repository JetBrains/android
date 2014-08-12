/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.navigator.nodes;

import com.google.common.collect.Lists;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PlatformIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/** {@link AndroidResGroupNode} groups together all the configuration specific alternatives of a single resource. */
public class AndroidResGroupNode extends ProjectViewNode<List<PsiFile>> implements DirectoryGroupNode {
  @NotNull private final String myResName;
  @NotNull private final AndroidFacet myFacet;
  @NotNull private final List<PsiFile> myFiles;

  public AndroidResGroupNode(@NotNull Project project,
                             @NotNull AndroidFacet facet,
                             @NotNull List<PsiFile> files,
                             @NotNull String resName,
                             @NotNull ViewSettings settings) {
    super(project, files, settings);
    myResName = resName;
    myFacet = facet;
    myFiles = files;
  }

  @NotNull
  @Override
  public PsiDirectory[] getDirectories() {
    List<PsiFile> psiFiles = getValue();
    PsiDirectory[] folders = new PsiDirectory[psiFiles.size()];
    for (int i = 0; i < psiFiles.size(); i++) {
      folders[i] = psiFiles.get(i).getParent();
    }
    return folders;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    for (PsiFile psiFile : myFiles) {
      if (psiFile.getVirtualFile().equals(file)) {
        return true;
      }
    }

    return false;
  }

  @NotNull
  @Override
  public Collection<? extends AbstractTreeNode> getChildren() {
    List<PsiFileNode> children = Lists.newArrayListWithExpectedSize(myFiles.size());
    for (PsiFile file : myFiles) {
      children.add(new AndroidResFileNode(myProject, file, getSettings(), myFacet));
    }
    return children;
  }

  @Override
  protected void update(PresentationData presentation) {
    presentation.setPresentableText(myResName);
    presentation.addText(myResName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    if (myFiles.size() > 1) {
      presentation.addText(String.format(" (%1$d)", myFiles.size()), SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
    presentation.setIcon(PlatformIcons.PACKAGE_ICON);
  }

  @Nullable
  @Override
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    StringBuilder sb = new StringBuilder(myResName);
    sb.append(" (");
    sb.append(myFiles.size());
    sb.append(")");
    return sb.toString();
  }
}
