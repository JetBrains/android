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
package com.android.tools.idea.navigator.nodes.other;

import com.android.tools.idea.navigator.nodes.FolderGroupNode;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ProjectViewDirectoryHelper;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;

public class NonAndroidSourceTypeNode extends ProjectViewNode<Module> implements FolderGroupNode {
  private final NonAndroidSourceType mySourceType;

  NonAndroidSourceTypeNode(@NotNull Project project,
                           @NotNull Module module,
                           @NotNull ViewSettings settings,
                           @NotNull NonAndroidSourceType type) {
    super(project, module, settings);
    mySourceType = type;
  }

  @Override
  @NotNull
  public Collection<? extends AbstractTreeNode> getChildren() {
    List<VirtualFile> sourceFolders = getSourceFolders();
    List<AbstractTreeNode> children = new ArrayList<>(sourceFolders.size());

    assert myProject != null;
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
  private List<VirtualFile> getSourceFolders() {
    Module module = getModule();
    ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    List<VirtualFile> folders = new ArrayList<>();

    ContentEntry[] contentEntries = rootManager.getContentEntries();
    for (ContentEntry entry : contentEntries) {
      List<SourceFolder> sources = entry.getSourceFolders(mySourceType.rootType);
      for (SourceFolder folder : sources) {
        VirtualFile file = folder.getFile();
        if (file != null) {
          folders.add(file);
        }
      }
    }

    return folders;
  }

  @NotNull
  private Module getModule() {
    Module module = getValue();
    assert module != null;
    return module;
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
    presentation.addText(mySourceType.presentableName, REGULAR_ATTRIBUTES);
    presentation.setPresentableText(mySourceType.presentableName);
    presentation.setIcon(mySourceType.icon);
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
    return mySourceType.presentableName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }

    NonAndroidSourceTypeNode that = (NonAndroidSourceTypeNode)o;

    if (mySourceType != that.mySourceType) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + mySourceType.hashCode();
    return result;
  }

  @Override
  @NotNull
  public PsiDirectory[] getFolders() {
    assert myProject != null;
    PsiManager psiManager = PsiManager.getInstance(myProject);
    List<VirtualFile> sourceFolders = getSourceFolders();
    List<PsiDirectory> folders = new ArrayList<>(sourceFolders.size());

    for (VirtualFile f : sourceFolders) {
      PsiDirectory dir = psiManager.findDirectory(f);
      if (dir != null) {
        folders.add(dir);
      }
    }
    return folders.toArray(new PsiDirectory[folders.size()]);
  }
}
