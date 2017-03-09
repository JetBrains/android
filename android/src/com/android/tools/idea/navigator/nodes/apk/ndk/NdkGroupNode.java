/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.navigator.nodes.apk.ndk;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import org.jetbrains.android.facet.AndroidSourceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.intellij.icons.AllIcons.Modules.SourceRoot;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;

public class NdkGroupNode extends ProjectViewNode<List<VirtualFile>> {
  @NotNull private final List<VirtualFile> myFiles;

  public NdkGroupNode(@NotNull Project project, @NotNull List<VirtualFile> files, @NotNull ViewSettings settings) {
    super(project, files, settings);
    myFiles = files;
  }

  @Override
  @NotNull
  public Collection<? extends AbstractTreeNode> getChildren() {
    List<AbstractTreeNode> children = new ArrayList<>();
    for (VirtualFile file : myFiles) {
      assert myProject != null;
      PsiDirectory psiFile = PsiManager.getInstance(myProject).findDirectory(file);
      if (psiFile != null) {
        children.add(new PsiDirectoryNode(myProject, psiFile, getSettings()));
      }
    }
    return children;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return myFiles.contains(file);
  }

  @Override
  protected void update(PresentationData presentation) {
    presentation.setIcon(SourceRoot);
    presentation.addText(getSourceType().getName(), REGULAR_ATTRIBUTES);
  }

  @Override
  @Nullable
  public Comparable getSortKey() {
    return getTypeSortKey();
  }

  @Override
  @Nullable
  public Comparable getTypeSortKey() {
    return getSourceType();
  }

  @NotNull
  private static AndroidSourceType getSourceType() {
    return AndroidSourceType.CPP;
  }
}
