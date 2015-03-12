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

import com.android.tools.idea.navigator.AndroidProjectTreeBuilder;
import com.android.tools.idea.navigator.AndroidProjectViewPane;
import com.google.common.collect.Lists;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ProjectViewDirectoryHelper;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidSourceType;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * {@link AndroidSourceTypeNode} is a virtual node in the package view of an Android module under which all sources
 * corresponding to a particular {@link org.jetbrains.android.facet.AndroidSourceType} are grouped together.
 */
public class AndroidSourceTypeNode extends ProjectViewNode<AndroidFacet> implements DirectoryGroupNode {
  @NotNull private final AndroidSourceType mySourceType;
  @NotNull private final Set<VirtualFile> mySourceRoots;
  @NotNull protected final AndroidProjectViewPane myProjectViewPane;

  public AndroidSourceTypeNode(@NotNull Project project,
                               @NotNull AndroidFacet facet,
                               @NotNull ViewSettings viewSettings,
                               @NotNull AndroidSourceType sourceType,
                               @NotNull Set<VirtualFile> sources,
                               @NotNull AndroidProjectViewPane projectViewPane) {
    super(project, facet, viewSettings);
    mySourceType = sourceType;
    mySourceRoots = sources;
    myProjectViewPane = projectViewPane;
  }

  @NotNull
  @Override
  public Collection<? extends AbstractTreeNode> getChildren() {
    List<AbstractTreeNode> children = Lists.newArrayList();
    ProjectViewDirectoryHelper projectViewDirectoryHelper = ProjectViewDirectoryHelper.getInstance(myProject);
    AndroidProjectTreeBuilder treeBuilder = (AndroidProjectTreeBuilder)myProjectViewPane.getTreeBuilder();

    for (PsiDirectory directory : getSourceDirectories()) {
      Collection<AbstractTreeNode> directoryChildren = projectViewDirectoryHelper.getDirectoryChildren(directory, getSettings(), true);

      children.addAll(annotateWithSourceProvider(directoryChildren));

      // Inform the tree builder of the node that this particular virtual file maps to
      treeBuilder.createMapping(directory.getVirtualFile(), this);
    }

    return children;
  }

  private Collection<AbstractTreeNode> annotateWithSourceProvider(Collection<AbstractTreeNode> directoryChildren) {
    List<AbstractTreeNode> children = Lists.newArrayListWithExpectedSize(directoryChildren.size());

    for (AbstractTreeNode child : directoryChildren) {
      if (child instanceof PsiDirectoryNode) {
        PsiDirectory directory = ((PsiDirectoryNode)child).getValue();
        children.add(new AndroidPsiDirectoryNode(myProject, directory, getSettings(), findSourceProvider(directory.getVirtualFile())));
      } else if (child instanceof PsiFileNode) {
        PsiFile file = ((PsiFileNode)child).getValue();
        children.add(new AndroidPsiFileNode(myProject, file, getSettings(), findSourceProvider(file.getVirtualFile())));
      } else {
        children.add(child);
      }
    }

    return children;
  }

  @Nullable
  private IdeaSourceProvider findSourceProvider(VirtualFile virtualFile) {
    for (IdeaSourceProvider provider : AndroidProjectViewPane.getSourceProviders(getValue())) {
      if (provider.containsFile(virtualFile)) {
        return provider;
      }
    }

    return null;
  }

  protected List<PsiDirectory> getSourceDirectories() {
    PsiManager psiManager = PsiManager.getInstance(myProject);
    List<PsiDirectory> psiDirectories = Lists.newArrayListWithExpectedSize(mySourceRoots.size());

    for (VirtualFile root : mySourceRoots) {
      if (!root.isValid()) {
        continue;
      }

      final PsiDirectory directory = psiManager.findDirectory(root);
      if (directory != null) {
        psiDirectories.add(directory);
      }
    }

    return psiDirectories;
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

  @Nullable
  @Override
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return mySourceType.getName();
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    //TODO: first check if the file is of my source type

    for (VirtualFile root : mySourceRoots) {
      if (VfsUtilCore.isAncestor(root, file, false)) {
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    AndroidSourceTypeNode that = (AndroidSourceTypeNode)o;

    if (mySourceType != that.mySourceType) return false;
    return mySourceRoots.equals(that.mySourceRoots);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + mySourceType.hashCode();
    for (VirtualFile root : mySourceRoots) {
      result = 31 * result + root.hashCode();
    }
    return result;
  }

  @NotNull
  @Override
  public PsiDirectory[] getDirectories() {
    List<PsiDirectory> folders = getSourceDirectories();
    return folders.toArray(new PsiDirectory[folders.size()]);
  }
}
