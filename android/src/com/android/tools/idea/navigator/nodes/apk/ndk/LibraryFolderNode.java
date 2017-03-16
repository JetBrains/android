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
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.intellij.icons.AllIcons.Nodes.Folder;
import static com.intellij.openapi.vfs.VfsUtilCore.isAncestor;

public class LibraryFolderNode extends ProjectViewNode<VirtualFile> {
  @NotNull private final VirtualFile myFolder;

  public LibraryFolderNode(@NotNull Project project, @NotNull VirtualFile folder, @NotNull ViewSettings settings) {
    super(project, folder, settings);
    myFolder = folder;
  }

  @Override
  @NotNull
  public Collection<? extends AbstractTreeNode> getChildren() {
    assert myProject != null;
    List<AbstractTreeNode> children = new ArrayList<>();
    ViewSettings settings = getSettings();
    for (VirtualFile child : myFolder.getChildren()) {
      if (child.isDirectory()) {
        children.add(new LibraryFolderNode(myProject, child, settings));
      }
      else if ("so".equals(child.getExtension())) {
        children.add(new LibraryNode(myProject, child, settings));
      }
    }

    return children;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return isAncestor(myFolder, file, false /* not strict */);
  }

  @Override
  protected void update(PresentationData presentation) {
    presentation.setIcon(Folder);
    presentation.setPresentableText(myFolder.getName());
  }

  @NotNull
  protected VirtualFile getFolder() {
    return myFolder;
  }

  @Override
  @Nullable
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return myFolder.getName();
  }
}
