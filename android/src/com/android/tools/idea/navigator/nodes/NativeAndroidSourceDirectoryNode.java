/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ProjectViewDirectoryHelper;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public class NativeAndroidSourceDirectoryNode extends PsiDirectoryNode {
  @NotNull private final Collection<String> myFileExtensions;
  @NotNull private final Collection<VirtualFile> mySourceFolders;
  @NotNull private final Collection<VirtualFile> mySourceFiles;

  public NativeAndroidSourceDirectoryNode(@NotNull Project project,
                                          @NotNull PsiDirectory dir,
                                          @NotNull ViewSettings settings,
                                          @NotNull Collection<String> fileExtensions,
                                          @NotNull Collection<VirtualFile> sourceFolders,
                                          @NotNull Collection<VirtualFile> sourceFiles) {
    super(project, dir, settings);
    myFileExtensions = fileExtensions;
    mySourceFolders = sourceFolders;
    mySourceFiles = sourceFiles;
  }

  @Override
  protected boolean shouldShowModuleName() {
    return false;
  }

  @Override
  protected boolean shouldShowSourcesRoot() {
    return false;
  }

  @Override
  protected void updateImpl(PresentationData presentation) {
    super.updateImpl(presentation);
    presentation.setIcon(AllIcons.Nodes.Folder);
  }

  @Override
  public Collection<AbstractTreeNode> getChildrenImpl() {
    PsiDirectory psiDirectory = getValue();
    if (psiDirectory == null) {
      return ImmutableList.of();
    }

    Collection<AbstractTreeNode> directoryChildren =
      ProjectViewDirectoryHelper.getInstance(myProject).getDirectoryChildren(psiDirectory, getSettings(), true);

    List<AbstractTreeNode> result = Lists.newArrayList();
    for (AbstractTreeNode child : directoryChildren) {
      Object value = child.getValue();
      if (value instanceof PsiFile) {
        VirtualFile file = ((PsiFile)value).getVirtualFile();
        if ((mySourceFolders.contains(psiDirectory.getVirtualFile()) && myFileExtensions.contains(file.getExtension())) ||
            mySourceFiles.contains(file)) {
          result.add(child);
        }
      }
      else if (value instanceof PsiDirectory) {
        VirtualFile dir = ((PsiDirectory)value).getVirtualFile();

        if (mySourceFolders.contains(dir) || mySourceFolders.contains(psiDirectory.getVirtualFile())) {
          result.add(new NativeAndroidSourceDirectoryNode(myProject, (PsiDirectory)value, getSettings(), myFileExtensions,
                                                          ImmutableList.of(dir), ImmutableList.of()));
          continue;
        }

        List<VirtualFile> childDirs = Lists.newArrayList();
        for (VirtualFile sourceFolder : mySourceFolders) {
          if (VfsUtilCore.isAncestor(dir, sourceFolder, true)) {
            childDirs.add(sourceFolder);
          }
        }
        List<VirtualFile> childFiles = Lists.newArrayList();
        for (VirtualFile file : mySourceFiles) {
          if (VfsUtilCore.isAncestor(dir, file, true)) {
            childFiles.add(file);
          }
        }

        if (!childDirs.isEmpty() || !childFiles.isEmpty()) {
          result.add(new NativeAndroidSourceDirectoryNode(myProject, (PsiDirectory)value, getSettings(), myFileExtensions,
                                                          childDirs, childFiles));
        }
      }
    }

    return result;
  }
}
