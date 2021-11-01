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

import static com.intellij.icons.AllIcons.Nodes.Folder;
import static com.intellij.openapi.util.io.FileUtil.getLocationRelativeToUserHome;
import static com.intellij.ui.SimpleTextAttributes.GRAY_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ProjectViewDirectoryHelper;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.projectView.impl.nodes.PsiFileSystemItemFilter;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import java.util.Collection;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NdkSourceFolderNode extends PsiDirectoryNode {
  private boolean myShowFolderPath;

  public NdkSourceFolderNode(@NotNull Project project,
                             @NotNull PsiDirectory folder,
                             @NotNull ViewSettings settings,
                             @Nullable PsiFileSystemItemFilter filter) {
    super(project, folder, settings, filter);
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
  protected void updateImpl(@NotNull PresentationData presentation) {
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
  public Collection<AbstractTreeNode<?>> getChildrenImpl() {
    PsiDirectory folder = getValue();
    if (folder == null) {
      return Collections.emptyList();
    }

    return ProjectViewDirectoryHelper.getInstance(myProject)
      .getDirectoryChildren(folder, getSettings(), true /* with subdirectories */, getFilter());
  }

  void setShowFolderPath(boolean showFolderPath) {
    myShowFolderPath = showFolderPath;
  }
}
