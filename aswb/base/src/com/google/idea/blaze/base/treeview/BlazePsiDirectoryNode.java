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

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import java.util.Collection;
import java.util.stream.Collectors;

/** A PsiDirectoryNode that doesn't render module names or source roots. */
public class BlazePsiDirectoryNode extends PsiDirectoryNode {

  public BlazePsiDirectoryNode(Project project, PsiDirectory directory, ViewSettings settings) {
    super(project, directory, settings);
  }

  @Override
  public String getQualifiedNameSortKey() {
    return toString();
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
  public Collection<AbstractTreeNode<?>> getChildrenImpl() {
    return wrapChildren(super.getChildrenImpl());
  }

  static Collection<AbstractTreeNode<?>> wrapChildren(Collection<AbstractTreeNode<?>> children) {
    return children.stream().map(n -> wrap(n)).collect(Collectors.toList());
  }

  private static AbstractTreeNode<?> wrap(AbstractTreeNode<?> node) {
    if (!(node instanceof PsiDirectoryNode)) {
      return node;
    }
    PsiDirectoryNode dir = (PsiDirectoryNode) node;
    return dir instanceof BlazePsiDirectoryNode
        ? dir
        : new BlazePsiDirectoryNode(dir.getProject(), dir.getValue(), dir.getSettings());
  }

  @Override
  public boolean isValid() {
    PsiDirectory psi = getValue();
    if (psi == null || !psi.isValid()) {
      return false;
    }
    return super.isValid() || (getSettings().isHideEmptyMiddlePackages() && isSourceRoot(psi));
  }

  private static boolean isSourceRoot(PsiDirectory psi) {
    return ProjectRootsUtil.isModuleSourceRoot(psi.getVirtualFile(), psi.getProject());
  }
}
