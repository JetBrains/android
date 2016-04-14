/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.tools.idea.gradle.facet.NativeAndroidGradleFacet;
import com.google.common.collect.Lists;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ProjectViewDirectoryHelper;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.android.facet.AndroidSourceType;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class NativeAndroidSourceTypeNode extends ProjectViewNode<Module> implements DirectoryGroupNode {
  private final AndroidSourceType mySourceType;

  public NativeAndroidSourceTypeNode(@NotNull Project project,
                                     @NotNull Module module,
                                     @NotNull ViewSettings settings,
                                     @NotNull AndroidSourceType type) {
    super(project, module, settings);
    mySourceType = type;
  }

  @NotNull
  @Override
  public Collection<? extends AbstractTreeNode> getChildren() {
    Collection<VirtualFile> sourceFolders = getSourceFolders();
    List<AbstractTreeNode> children = Lists.newArrayListWithExpectedSize(sourceFolders.size());

    PsiManager psiManager = PsiManager.getInstance(myProject);
    ProjectViewDirectoryHelper directoryHelper = ProjectViewDirectoryHelper.getInstance(myProject);
    for (VirtualFile file : sourceFolders) {
      PsiDirectory dir = psiManager.findDirectory(file);
      if (dir != null) {
        children.addAll(directoryHelper.getDirectoryChildren(dir, getSettings(), true));
      }
    }

    return children;
  }

  @NotNull
  private Collection<VirtualFile> getSourceFolders() {
    Module module = getValue();
    NativeAndroidGradleFacet facet = NativeAndroidGradleFacet.getInstance(module);
    if (facet == null || facet.getNativeAndroidGradleModel() == null) {
      return Collections.emptyList();
    }
    return mySourceType.getSources(IdeaSourceProvider.create(facet));
  }


  @Override
  protected void update(PresentationData presentation) {
    presentation.addText(mySourceType.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);

    Icon icon = mySourceType.getIcon();
    if (icon != null) {
      presentation.setIcon(icon);
    }
    presentation.setPresentableText(mySourceType.getName());
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    for (VirtualFile folder : getSourceFolders()) {
      if (VfsUtilCore.isAncestor(folder, file, false)) {
        return true;
      }
    }

    return false;
  }

  @Nullable
  @Override
  public Comparable getSortKey() {
    return mySourceType;
  }

  @Nullable
  @Override
  public Comparable getTypeSortKey() {
    return mySourceType;
  }

  @Nullable
  @Override
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return mySourceType.getName();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    NativeAndroidSourceTypeNode that = (NativeAndroidSourceTypeNode)o;

    if (mySourceType != that.mySourceType) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + mySourceType.hashCode();
    return result;
  }

  @NotNull
  @Override
  public PsiDirectory[] getDirectories() {
    PsiManager psiManager = PsiManager.getInstance(myProject);
    Collection<VirtualFile> sourceFolders = getSourceFolders();
    List<PsiDirectory> psiDirectories = Lists.newArrayListWithExpectedSize(sourceFolders.size());

    for (VirtualFile f : sourceFolders) {
      PsiDirectory dir = psiManager.findDirectory(f);
      if (dir != null) {
        psiDirectories.add(dir);
      }
    }

    return psiDirectories.toArray(new PsiDirectory[psiDirectories.size()]);
  }
}
