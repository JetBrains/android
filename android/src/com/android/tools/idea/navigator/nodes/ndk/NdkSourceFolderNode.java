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
package com.android.tools.idea.navigator.nodes.ndk;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ProjectViewDirectoryHelper;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.icons.AllIcons.Nodes.Folder;
import static com.intellij.openapi.util.io.FileUtil.getLocationRelativeToUserHome;
import static com.intellij.openapi.vfs.VfsUtilCore.isAncestor;
import static com.intellij.ui.SimpleTextAttributes.GRAY_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;

public class NdkSourceFolderNode extends PsiDirectoryNode {
  @NotNull private final Collection<String> myFileExtensions;
  @NotNull private final Collection<VirtualFile> mySourceFolders;
  @NotNull private final Collection<VirtualFile> mySourceFiles;

  private boolean myShowFolderPath;

  public NdkSourceFolderNode(@NotNull Project project,
                             @NotNull PsiDirectory folder,
                             @NotNull ViewSettings settings,
                             @NotNull Collection<String> fileExtensions,
                             @NotNull Collection<VirtualFile> sourceFolders,
                             @NotNull Collection<VirtualFile> sourceFiles) {
    super(project, folder, settings);
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
    VirtualFile folder = getVirtualFile();
    assert folder != null;
    presentation.setPresentableText(folder.getName());
    presentation.addText(folder.getName(), REGULAR_ATTRIBUTES);
    if (myShowFolderPath) {
      String text = String.format(" (%1$s)", getLocationRelativeToUserHome(folder.getPresentableUrl()));
      presentation.addText(text, GRAY_ATTRIBUTES);
    }
    presentation.setIcon(Folder);
  }

  @Override
  public Collection<AbstractTreeNode> getChildrenImpl() {
    PsiDirectory folder = getValue();
    if (folder == null) {
      return Collections.emptyList();
    }

    Collection<AbstractTreeNode> folderChildren =
      ProjectViewDirectoryHelper.getInstance(myProject).getDirectoryChildren(folder, getSettings(), true /* with subdirectories */);

    List<AbstractTreeNode> result = new ArrayList<>();
    for (AbstractTreeNode child : folderChildren) {
      Object value = child.getValue();
      if (value instanceof PsiFile) {
        VirtualFile file = ((PsiFile)value).getVirtualFile();
        if ((mySourceFolders.contains(folder.getVirtualFile()) && myFileExtensions.contains(file.getExtension())) ||
            mySourceFiles.contains(file)) {
          result.add(child);
        }
      }
      else if (value instanceof PsiDirectory) {
        VirtualFile childFolder = ((PsiDirectory)value).getVirtualFile();

        Project project = getNotNullProject();
        if (mySourceFolders.contains(childFolder) || mySourceFolders.contains(folder.getVirtualFile())) {
          result.add(new NdkSourceFolderNode(project, (PsiDirectory)value, getSettings(), myFileExtensions,
                                             Collections.singletonList(childFolder), Collections.emptyList()));
          continue;
        }

        List<VirtualFile> childFolders = new ArrayList<>();
        for (VirtualFile sourceFolder : mySourceFolders) {
          if (isAncestor(childFolder, sourceFolder, true)) {
            childFolders.add(sourceFolder);
          }
        }
        List<VirtualFile> childFiles = new ArrayList<>();
        for (VirtualFile file : mySourceFiles) {
          if (isAncestor(childFolder, file, true)) {
            childFiles.add(file);
          }
        }

        if (!childFolders.isEmpty() || !childFiles.isEmpty()) {
          result.add(new NdkSourceFolderNode(project, (PsiDirectory)value, getSettings(), myFileExtensions, childFolders, childFiles));
        }
      }
    }

    return result;
  }

  @NotNull
  private Project getNotNullProject() {
    assert myProject != null;
    return myProject;
  }

  void setShowFolderPath(boolean showFolderPath) {
    myShowFolderPath = showFolderPath;
  }
}
