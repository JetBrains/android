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
import com.google.common.collect.Sets;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ProjectViewDirectoryHelper;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
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
public class AndroidSourceTypeNode extends ProjectViewNode<AndroidFacet> implements AndroidProjectViewNode {
  @NotNull private final AndroidSourceType mySourceType;
  @NotNull private final Iterable<IdeaSourceProvider> mySourceProviders;
  @NotNull private final AndroidProjectViewPane myProjectViewPane;

  public AndroidSourceTypeNode(@NotNull Project project,
                               @NotNull AndroidFacet facet,
                               @NotNull ViewSettings viewSettings,
                               @NotNull AndroidSourceType sourceType,
                               @NotNull Iterable<IdeaSourceProvider> sourceProviders,
                               @NotNull AndroidProjectViewPane projectViewPane) {
    super(project, facet, viewSettings);
    mySourceType = sourceType;
    mySourceProviders = sourceProviders;
    myProjectViewPane = projectViewPane;
  }

  @NotNull
  @Override
  public Collection<? extends AbstractTreeNode> getChildren() {
    assert myProject != null;

    // This node is only visible in the Android Pane
    AndroidProjectViewPane pane = (AndroidProjectViewPane)ProjectView.getInstance(myProject).getCurrentProjectViewPane();

    List<AbstractTreeNode> children = Lists.newArrayList();
    final PsiManager psiManager = PsiManager.getInstance(myProject);
    ProjectViewDirectoryHelper projectViewDirectoryHelper = ProjectViewDirectoryHelper.getInstance(myProject);
    AndroidProjectTreeBuilder treeBuilder = (AndroidProjectTreeBuilder)myProjectViewPane.getTreeBuilder();

    // Add sources of given type from all the source providers
    for (IdeaSourceProvider sourceProvider : mySourceProviders) {
      for (VirtualFile file : mySourceType.getSources(sourceProvider)) {
        final PsiDirectory directory = psiManager.findDirectory(file);
        if (directory != null) {
          Collection<AbstractTreeNode> directoryChildren = projectViewDirectoryHelper.getDirectoryChildren(directory, getSettings(), true);
          children.addAll(directoryChildren);

          // Inform the tree builder of the node that this particular virtual file maps to
          treeBuilder.createMapping(directory.getVirtualFile(), this);
        }
      }
    }

    return children;
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
    for (IdeaSourceProvider sourceProvider : mySourceProviders) {
      for (VirtualFile folder : mySourceType.getSources(sourceProvider)) {
        if (VfsUtilCore.isAncestor(folder, file, false)) {
          return true;
        }
      }
    }

    return false;
  }

  @Nullable
  @Override
  public Comparable getSortKey() {
    return mySourceType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    AndroidSourceTypeNode that = (AndroidSourceTypeNode)o;

    if (mySourceType != that.mySourceType) return false;
    if (mySourceProviders != that.mySourceProviders) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + mySourceType.hashCode();
    result = 31 * result + mySourceProviders.hashCode();
    return result;
  }

  @NotNull
  @Override
  public AndroidFacet getAndroidFacet() {
    return getValue();
  }

  @NotNull
  @Override
  public PsiDirectory[] getDirectories() {
    PsiManager psiManager = PsiManager.getInstance(myProject);
    Set<PsiDirectory> folders = Sets.newHashSet();

    for (IdeaSourceProvider provider : mySourceProviders) {
      for (VirtualFile vf : mySourceType.getSources(provider)) {
        PsiDirectory folder = psiManager.findDirectory(vf);
        if (folder != null) {
          folders.add(folder);
        }
      }
    }

    return folders.toArray(new PsiDirectory[folders.size()]);
  }
}
