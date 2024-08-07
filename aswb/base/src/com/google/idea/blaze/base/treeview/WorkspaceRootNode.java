/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.treeview;

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.ui.SimpleTextAttributes;
import java.util.Collection;
import java.util.List;

/**
 * Workspace root node.
 *
 * <p>
 *
 * <p>Customizes rendering of the workspace root node to cut out the full absolute path of the
 * workspace directory.
 */
public class WorkspaceRootNode extends PsiDirectoryNode {
  private final WorkspaceRoot workspaceRoot;

  public WorkspaceRootNode(
      Project project, WorkspaceRoot workspaceRoot, PsiDirectory value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
    this.workspaceRoot = workspaceRoot;
  }

  @Override
  public Collection<AbstractTreeNode<?>> getChildrenImpl() {
    if (!BlazeUserSettings.getInstance().getCollapseProjectView()) {
      return getWrappedChildren();
    }
    Project project = getProject();
    if (project == null) {
      return getWrappedChildren();
    }
    List<AbstractTreeNode<?>> children = Lists.newArrayList();
    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    if (projectViewSet == null) {
      return getWrappedChildren();
    }

    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, Blaze.getBuildSystemName(project))
            .add(projectViewSet)
            .build();
    if (importRoots.rootDirectories().stream().anyMatch(WorkspacePath::isWorkspaceRoot)) {
      return getWrappedChildren();
    }
    for (WorkspacePath workspacePath : importRoots.rootDirectories()) {
      VirtualFile virtualFile =
          VfsUtil.findFileByIoFile(workspaceRoot.fileForPath(workspacePath), false);
      if (virtualFile == null) {
        continue;
      }
      PsiDirectory psiDirectory = PsiManager.getInstance(project).findDirectory(virtualFile);
      if (psiDirectory == null) {
        continue;
      }
      children.add(new BlazePsiDirectoryRootNode(project, psiDirectory, getSettings()));
    }
    if (children.isEmpty()) {
      return getWrappedChildren();
    }
    return children;
  }

  private Collection<AbstractTreeNode<?>> getWrappedChildren() {
    return BlazePsiDirectoryNode.wrapChildren(super.getChildrenImpl());
  }

  @Override
  protected void updateImpl(PresentationData data) {
    super.updateImpl(data);
    PsiDirectory psiDirectory = getValue();
    assert psiDirectory != null;
    String text = psiDirectory.getName();

    data.setPresentableText(text);
    data.clearText();
    data.addText(text, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    data.setLocationString("");
  }
}
