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

import com.android.tools.idea.navigator.nodes.ndk.includes.utils.LexicalIncludePaths;
import com.android.tools.idea.navigator.nodes.ndk.includes.utils.VirtualFiles;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;


/**
 * Methods for creating and dealing with visual representation nodes for include folders.
 */
public final class IncludeViewNodes {

  /**
   * Method that produces a collection of folders and files that represents a set of includes in order.
   *
   * @param includes              The ordered set of include folders.
   * @param excludesVirtualFiles  The set of folders to be excluded.
   * @param folderParentIsObvious Whether or not the parent folder of these files and folders is visibly obvious.
   *                              If not, then show a hint path next to them.
   * @param project               The project
   * @param settings              The view settings
   * @return A collection of tree nodes for the folders and files.
   */
  @NotNull
  public static Collection<AbstractTreeNode<?>> getIncludeFolderNodesWithShadowing(@NotNull Collection<File> includes,
                                                                                @NotNull ImmutableList<VirtualFile> excludesVirtualFiles,
                                                                                boolean folderParentIsObvious,
                                                                                @NotNull Project project,
                                                                                @NotNull ViewSettings settings) {
    List<AbstractTreeNode<?>> result = new ArrayList<>();
    Set<String> baseNameSeenAlready = new HashSet<>();
    PsiManager psiManager = PsiManager.getInstance(project);
    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    for (File include : includes) {
      VirtualFile folder = fileSystem.findFileByIoFile(include);
      if (folder == null) {
        continue;
      }
      PsiDirectory psiDirectory = psiManager.findDirectory(folder);
      if (psiDirectory != null) {
        psiDirectory.processChildren(element -> {
          if (VirtualFiles.isElementAncestorOfExclude(element, excludesVirtualFiles)) {
            // This file or folder is in the set to be excluded.
            return true;
          }
          if (baseNameSeenAlready.contains(element.getName())) {
            // These are files and folders that have been shadowed by prior include folders.
            // Could accumulate these and show them in a separated node.
            return true;
          }
          baseNameSeenAlready.add(element.getName());
          if (element instanceof PsiDirectory) {
            PsiDirectory concrete = (PsiDirectory)element;
            PsiIncludeDirectoryView node =
              new PsiIncludeDirectoryView(project, excludesVirtualFiles, folderParentIsObvious, concrete, settings, psiDirectory);
            if (!node.getChildren().isEmpty()) {
              result.add(node);
            }
            return true;
          }
          if (element instanceof PsiFile) {
            PsiFile concrete = (PsiFile)element;
            if (!LexicalIncludePaths.hasHeaderExtension(concrete.getName())) {
              return true;
            }
            result.add(new PsiFileNode(project, concrete, settings));
            return true;
          }
          return true;
        });
      }
    }
    return result;
  }
}
