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
package com.android.tools.idea.navigator.nodes.ndk.includes.view;

import static com.android.tools.idea.navigator.nodes.android.AndroidPsiDirectoryNode.tempGetVirtualFile;

import com.android.tools.idea.navigator.nodes.ndk.includes.utils.LexicalIncludePaths;
import com.android.tools.idea.navigator.nodes.ndk.includes.utils.VirtualFiles;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ProjectViewDirectoryHelper;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wraps a view over a filesystem directory that represents a single include folder.
 * There is a set of exclusions that is used to hide sub-folders that would be contained in other components.
 */
public class PsiIncludeDirectoryView extends PsiDirectoryNode {
  @NotNull private final ImmutableList<VirtualFile> myVirtualFileExcludes;
  @Nullable private final PsiDirectory myOwner;

  private final boolean myFolderParentIsObvious;

  public PsiIncludeDirectoryView(@Nullable Project project,
                                 @NotNull ImmutableList<VirtualFile> virtualFileExcludes,
                                 boolean folderParentIsObvious,
                                 @NotNull PsiDirectory value,
                                 @NotNull ViewSettings viewSettings,
                                 @Nullable PsiDirectory owner) {
    super(project, value, viewSettings);
    this.myFolderParentIsObvious = folderParentIsObvious;
    this.myVirtualFileExcludes = virtualFileExcludes;
    this.myOwner = owner;
  }

  @NotNull
  private PsiDirectory getPsiDirectory() {
    PsiDirectory value = getValue();
    assert value != null;
    return value;
  }

  @Override
  @NotNull
  public Collection<AbstractTreeNode<?>> getChildrenImpl() {
    List<AbstractTreeNode<?>> result = new ArrayList<>();
    PsiDirectory nodeValue = getPsiDirectory();
    nodeValue.processChildren(element -> {
      if (VirtualFiles.isElementAncestorOfExclude(element, myVirtualFileExcludes)) {
        // This file or folder is in the set to be excluded.
        return true;
      }

      if (element instanceof PsiDirectory) {
        PsiDirectory concrete = (PsiDirectory)element;
        PsiDirectoryNode node = new PsiIncludeDirectoryView(getProject(), myVirtualFileExcludes, true, concrete, getSettings(), nodeValue);
        result.add(node);
        return true;
      }
      if (element instanceof PsiFile) {
        PsiFile concrete = (PsiFile)element;
        if (!LexicalIncludePaths.hasHeaderExtension(concrete.getName())) {
          return true;
        }
        result.add(new PsiFileNode(getProject(), concrete, getSettings()));
        return true;
      }
      throw new RuntimeException(element.getClass().toString());
    });
    return result;
  }

  @Override
  protected void updateImpl(@NotNull PresentationData data) {
    super.updateImpl(data);
    String location = data.getLocationString();
    if ((location == null || location.isEmpty()) && !myFolderParentIsObvious) {
      PsiDirectory nodeValue = getPsiDirectory();
      data.setLocationString(ProjectViewDirectoryHelper.getInstance(getProject()).getLocationString(nodeValue, true, true));
    }
  }

  @Override
  public boolean canRepresent(Object element) {
    if (super.canRepresent(element)) return true;
    VirtualFile file = tempGetVirtualFile(element);
    PsiDirectory nodeValue = getValue();
    if (file == null || nodeValue == null || myOwner == null) return false;
    return ProjectViewDirectoryHelper.getInstance(myProject)
      .canRepresent(file, nodeValue, myOwner, getSettings());
  }
}
