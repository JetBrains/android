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
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.icons.AllIcons.FileTypes.JavaClass;
import static com.intellij.ui.JBColor.GRAY;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.STYLE_WAVED;

public class LibraryNode extends ProjectViewNode<NativeLibrary> {
  @NotNull private final NativeLibrary myLibrary;
  @NotNull private final String myLibraryName;

  public LibraryNode(@NotNull Project project,
                     @NotNull NativeLibrary library,
                     @NotNull String libraryName,
                     @NotNull ViewSettings settings) {
    super(project, library, settings);
    myLibrary = library;
    myLibraryName = libraryName;
  }

  @Override
  @NotNull
  public Collection<? extends AbstractTreeNode> getChildren() {
    List<String> sourceFolderPaths = myLibrary.sourceFolderPaths;
    if (sourceFolderPaths.isEmpty()) {
      return Collections.emptyList();
    }
    assert myProject != null;
    List<AbstractTreeNode> children = new ArrayList<>();
    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    for (String path : sourceFolderPaths) {
      VirtualFile folder = fileSystem.findFileByPath(path);
      if (folder != null) {
        PsiDirectory psiFile = PsiManager.getInstance(myProject).findDirectory(folder);
        if (psiFile != null) {
          children.add(new PsiDirectoryNode(myProject, psiFile, getSettings()));
        }
      }
    }
    return children;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return false;
  }

  @Override
  protected void update(PresentationData presentation) {
    presentation.setIcon(JavaClass);
    boolean hasDebugSymbols = myLibrary.hasDebugSymbols;
    SimpleTextAttributes attributes = hasDebugSymbols ? REGULAR_ATTRIBUTES : new SimpleTextAttributes(STYLE_WAVED, null, GRAY);
    presentation.addText(myLibraryName, attributes);
    if (!hasDebugSymbols) {
      presentation.setTooltip("Library does not have debug symbols");
    }
  }

  @Nullable
  @Override
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return myLibraryName;
  }
}
