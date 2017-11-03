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

import com.android.tools.idea.apk.paths.PathTree;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static com.android.tools.idea.navigator.nodes.apk.ndk.PathTrees.findSourceFolders;
import static com.intellij.openapi.vfs.VfsUtilCore.isAncestor;
import static com.intellij.ui.SimpleTextAttributes.GRAY_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;

public class NdkSourceNode extends ProjectViewNode<File> {
  @NotNull private final File myNdkPath;
  @NotNull private final List<PsiDirectory> mySourceFolders;
  @NotNull private final SourceCodeFilter myFilter;

  public NdkSourceNode(@NotNull Project project,
                       @NotNull File ndkPath,
                       @NotNull PathTree ndkPathTree,
                       @NotNull SourceCodeFilter filter,
                       @NotNull ViewSettings viewSettings) {
    super(project, ndkPath, viewSettings);
    myNdkPath = ndkPath;
    myFilter = filter;
    mySourceFolders = findSourceFolders(ndkPathTree, ndkPath.getPath(), project);
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    for (PsiDirectory sourceFolder : mySourceFolders) {
      if (isAncestor(sourceFolder.getVirtualFile(), file, false /* no strict */)) {
        return true;
      }
    }
    return false;
  }

  @Override
  @NotNull
  public Collection<? extends AbstractTreeNode> getChildren() {
    List<AbstractTreeNode> children = new ArrayList<>();

    ViewSettings settings = getSettings();
    for (PsiDirectory folder : mySourceFolders) {
      children.add(new PsiDirectoryNode(myProject, folder, settings, myFilter));
    }

    return children;
  }

  @Override
  protected void update(PresentationData presentation) {
    presentation.setIcon(AndroidIcons.Android);
    presentation.addText("NDK ", REGULAR_ATTRIBUTES);
    presentation.addText(myNdkPath.getPath(), GRAY_ATTRIBUTES);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof NdkSourceNode)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    NdkSourceNode that = (NdkSourceNode)o;
    return Objects.equals(myNdkPath, that.myNdkPath) &&
           Objects.equals(mySourceFolders, that.mySourceFolders) &&
           Objects.equals(myFilter, that.myFilter);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), myNdkPath, mySourceFolders, myFilter);
  }
}
