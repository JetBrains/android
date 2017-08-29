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

import com.android.tools.idea.apk.debugging.NativeLibrary;
import com.android.tools.idea.apk.paths.PathNode;
import com.android.tools.idea.apk.paths.PathTree;
import com.google.common.base.Joiner;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiBinaryFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.ui.SimpleTextAttributes.GRAY_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;
import static icons.StudioIcons.Shell.Filetree.LIBRARY_MODULE;
import static java.io.File.separatorChar;

public class LibraryNode extends ProjectViewNode<NativeLibrary> {
  @NotNull final NativeLibrary myLibrary;
  @NotNull private final String myLibraryName;

  public LibraryNode(@NotNull Project project, @NotNull NativeLibrary library, @NotNull ViewSettings settings) {
    super(project, library, settings);
    myLibrary = library;
    myLibraryName = getLibraryName();
  }

  @NotNull
  private String getLibraryName() {
    VirtualFile file = getFirstFile();
    return file != null ? file.getNameWithoutExtension() : myLibrary.name;
  }

  @Nullable
  protected VirtualFile getFirstFile() {
    List<VirtualFile> files = myLibrary.getSharedObjectFiles();
    return files.isEmpty() ? null : files.get(0);
  }

  @Override
  @NotNull
  public Collection<? extends AbstractTreeNode> getChildren() {
    assert myProject != null;
    List<AbstractTreeNode> children = new ArrayList<>();
    ViewSettings settings = getSettings();
    children.add(new LibraryFileNode(myProject, myLibrary, settings));

    List<String> sourceFolderPaths = new ArrayList<>(myLibrary.getSourceFolderPaths());
    if (!sourceFolderPaths.isEmpty()) {
      // Organize source folders in a tree.
      PathTree tree = new PathTree();
      for (String path : sourceFolderPaths) {
        tree.addPath(path, separatorChar);
      }

      // Only add the root nodes. PsiDirectoryNode will populate the children automatically.
      List<PathNode> rootNodes = new ArrayList<>();
      removeEmptyRoots(tree.getChildren(), rootNodes);

      // This filter will ensure only the folders that are considered "source folders" show up in the tree.
      SourceCodeFilter filter = new SourceCodeFilter(sourceFolderPaths);

      for (PathNode pathNode : rootNodes) {
        String path = pathNode.getPath();
        PsiDirectory psiFolder = findDirectory(path);
        if (psiFolder != null) {
          children.add(new PsiDirectoryNode(myProject, psiFolder, settings, filter));
        }
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
  private PsiDirectory findDirectory(@NotNull String path) {
    VirtualFile folder = LocalFileSystem.getInstance().findFileByPath(path);
    if (folder != null) {
      return PsiManager.getInstance(myProject).findDirectory(folder);
    }
    return null;
  }

  @Override
  public boolean canRepresent(Object element) {
    if (element instanceof PsiBinaryFile) {
      PsiBinaryFile binaryFile = (PsiBinaryFile)element;
      VirtualFile file = binaryFile.getVirtualFile();
      if (file != null) {
        return contains(file);
      }
    }
    if (element instanceof VirtualFile) {
      VirtualFile file = (VirtualFile)element;
      return contains(file);
    }
    return false;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return myLibrary.sharedObjectFilesByAbi.containsValue(file);
  }

  @Override
  protected void update(PresentationData presentation) {
    presentation.setIcon(LIBRARY_MODULE);
    presentation.addText(myLibraryName, REGULAR_ATTRIBUTES);

    String abis = Joiner.on(", ").join(myLibrary.abis);
    presentation.addText(" (" + abis + ")", GRAY_ATTRIBUTES);
  }

  @Override
  public boolean isAlwaysExpand() {
    return true;
  }

  @Override
  public boolean isAlwaysShowPlus() {
    return true;
  }

  @Nullable
  @Override
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return myLibraryName;
  }
}
