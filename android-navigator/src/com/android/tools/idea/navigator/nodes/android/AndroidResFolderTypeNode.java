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
package com.android.tools.idea.navigator.nodes.android;

import com.android.resources.ResourceFolderType;
import com.android.tools.idea.navigator.nodes.FolderGroupNode;
import com.android.tools.idea.navigator.nodes.GroupNodes;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.intellij.openapi.vfs.VfsUtilCore.isAncestor;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;

public class AndroidResFolderTypeNode extends ProjectViewNode<List<PsiDirectory>> implements FolderGroupNode {
  @NotNull private final AndroidFacet myFacet;
  @NotNull private final ResourceFolderType myFolderType;

  AndroidResFolderTypeNode(@NotNull Project project,
                           @NotNull AndroidFacet androidFacet,
                           @NotNull List<PsiDirectory> folders,
                           @NotNull ViewSettings settings,
                           @NotNull ResourceFolderType folderType) {
    super(project, folders, settings);
    myFacet = androidFacet;
    myFolderType = folderType;
  }

  @Override
  @NotNull
  public List<PsiDirectory> getFolders() {
    return getResFolders();
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    for (PsiDirectory resFolder : getResFolders()) {
      VirtualFile folder = resFolder.getVirtualFile();
      if (isAncestor(folder, file, false)) {
        return true;
      }
    }

    return false;
  }

  @Override
  public boolean canRepresent(Object element) {
    return GroupNodes.canRepresent(this, element);
  }

  @Override
  @NotNull
  public Collection<? extends AbstractTreeNode<?>> getChildren() {
    // all resource folders of a given folder type
    Multimap<String, PsiFile> multimap = HashMultimap.create();
    for (PsiDirectory resFolder : getResFolders()) {
      if (!resFolder.isValid()) continue;
      for (PsiFile file : resFolder.getFiles()) {
        String resName = FileUtilRt.getNameWithoutExtension(file.getName());
        multimap.put(resName, file);
      }
    }

    assert myProject != null;
    List<AbstractTreeNode<?>> children = new ArrayList<>(multimap.size());
    for (String resName : multimap.keySet()) {
      List<PsiFile> files = new ArrayList<>(multimap.get(resName));
      if (files.size() > 1) {
        children.add(new AndroidResGroupNode(myProject, myFacet, files, resName, getSettings()));
      }
      else {
        children.add(new AndroidResFileNode(myProject, files.get(0), getSettings(), myFacet));
      }
    }
    return children;
  }

  @NotNull
  private List<PsiDirectory> getResFolders() {
    List<PsiDirectory> folders = getValue();
    assert folders != null;
    return folders;
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
    presentation.addText(myFolderType.getName(), REGULAR_ATTRIBUTES);
    presentation.setIcon(IconManager.getInstance().getPlatformIcon(PlatformIcons.Package));
    presentation.setPresentableText(myFolderType.getName());
  }

  @Override
  @Nullable
  public Comparable getSortKey() {
    return myFolderType;
  }

  @Override
  @Nullable
  public Comparable getTypeSortKey() {
    return myFolderType;
  }

  @Override
  @Nullable
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return myFolderType.getName();
  }
}
