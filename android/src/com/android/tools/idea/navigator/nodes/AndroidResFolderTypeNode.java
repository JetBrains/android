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

import com.android.resources.ResourceFolderType;
import com.android.tools.idea.navigator.AndroidProjectViewPane;
import com.google.common.collect.*;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.IteratorUtils;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AndroidResFolderTypeNode extends ProjectViewNode<List<PsiDirectory>> implements AndroidProjectViewNode {
  @NotNull private final AndroidFacet myFacet;
  @NotNull private final ResourceFolderType myFolderType;
  @NotNull private final List<IdeaSourceProvider> mySourceProviders;
  @NotNull private final AndroidProjectViewPane myProjectViewPane;

  public AndroidResFolderTypeNode(@NotNull Project project,
                                  @NotNull AndroidFacet facet,
                                  @NotNull List<PsiDirectory> folders,
                                  @NotNull ViewSettings settings,
                                  @NotNull ResourceFolderType folderType,
                                  @NotNull List<IdeaSourceProvider> sourceProviders,
                                  @NotNull AndroidProjectViewPane projectViewPane) {
    super(project, folders, settings);
    myFacet = facet;
    myFolderType = folderType;
    mySourceProviders = sourceProviders;
    myProjectViewPane = projectViewPane;
  }

  @NotNull
  @Override
  public AndroidFacet getAndroidFacet() {
    return myFacet;
  }

  @NotNull
  @Override
  public PsiDirectory[] getDirectories() {
    List<PsiDirectory> dirs = getValue();
    return dirs.toArray(new PsiDirectory[dirs.size()]);
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    for (PsiDirectory psiDirectory : getValue()) {
      final VirtualFile folder = psiDirectory.getVirtualFile();
      if (VfsUtilCore.isAncestor(folder, file, true)) {
        return true;
      }
    }

    return false;
  }

  @NotNull
  @Override
  public Collection<? extends AbstractTreeNode> getChildren() {
    // all resource folders of a given folder type
    List<PsiDirectory> folders = getValue();

    Multimap<String,PsiFile> multimap = HashMultimap.create();
    for (PsiDirectory res : folders) {
      for (PsiFile file : res.getFiles()) {
        String resName = file.getName();
        multimap.put(resName, file);
      }
    }

    List<AbstractTreeNode> children = Lists.newArrayListWithExpectedSize(multimap.size());
    for (String resName : multimap.keySet()) {
      List<PsiFile> files = Lists.newArrayList(multimap.get(resName));
      if (files.size() > 1) {
        children.add(new AndroidResGroupNode(myProject, myFacet, files, resName, getSettings(), mySourceProviders));
      } else {
        children.add(new AndroidResFileNode(myProject, files.get(0), getSettings(), mySourceProviders));
      }
    }
    return children;
  }

  @Override
  protected void update(PresentationData presentation) {
    presentation.addText(myFolderType.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    presentation.setIcon(PlatformIcons.PACKAGE_ICON);
    presentation.setPresentableText(myFolderType.getName());
  }

  @Nullable
  @Override
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return myFolderType.getName();
  }
}
