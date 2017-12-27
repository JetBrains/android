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

import com.android.tools.idea.apk.paths.PathNode;
import com.android.tools.idea.apk.paths.PathTree;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.intellij.openapi.util.io.FileUtil.isAncestor;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

final class PathTrees {
  private PathTrees() {
  }

  @NotNull
  static List<PsiDirectory> findSourceFolders(@NotNull PathTree tree, @NotNull String basePath, @NotNull Project project) {
    List<PsiDirectory> children = new ArrayList<>();

    List<PathNode> nodes = new ArrayList<>();
    removeEmptyRoots(tree.getChildren(), nodes);

    for (PathNode pathNode : nodes) {
      addRoots(pathNode, basePath, children, project);
    }
    return children;
  }

  // Only add the paths that belong to subfolders of 'basePath', excluding the folder with path equal to 'basePath'.
  private static void addRoots(@NotNull PathNode pathNode,
                               @NotNull String basePath,
                               @NotNull List<PsiDirectory> roots,
                               @NotNull Project project) {
    String path = pathNode.getPath();
    if (isAncestor(basePath, path, true /* strict */)) {
      roots.add(findFolder(path, project));
      return;
    }
    for (PathNode child : pathNode.getChildren()) {
      addRoots(child, basePath, roots, project);
    }
  }

  @NotNull
  static List<AbstractTreeNode> getSourceFolderNodes(@NotNull PathTree tree,
                                                     @NotNull SourceCodeFilter filter,
                                                     @NotNull Project project,
                                                     @NotNull ViewSettings settings) {
    List<AbstractTreeNode> children = new ArrayList<>();

    // Only add the root nodes. PsiDirectoryNode will populate the children automatically.
    List<PathNode> rootSrcNodes = new ArrayList<>();
    removeEmptyRoots(tree.getChildren(), rootSrcNodes);

    for (PathNode pathNode : rootSrcNodes) {
      String path = pathNode.getPath();
      PsiDirectory psiFolder = findFolder(path, project);
      if (psiFolder != null) {
        children.add(new PsiDirectoryNode(project, psiFolder, settings, filter));
      }
    }

    return children;
  }

  private static void removeEmptyRoots(@NotNull Collection<PathNode> nodes, @NotNull List<PathNode> rootNodes) {
    for (PathNode node : nodes) {
      String path = node.getPath();
      if (isNotEmpty(path)) {
        rootNodes.add(node);
        continue;
      }
      removeEmptyRoots(node.getChildren(), rootNodes);
    }
  }

  @Nullable
  private static PsiDirectory findFolder(@NotNull String path, @NotNull Project project) {
    VirtualFile folder = LocalFileSystem.getInstance().findFileByPath(path);
    if (folder != null) {
      return PsiManager.getInstance(project).findDirectory(folder);
    }
    return null;
  }
}
