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
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

import static com.intellij.icons.AllIcons.FileTypes.JavaClass;
import static com.intellij.ui.JBColor.GRAY;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.STYLE_WAVED;

public class LibraryFileNode extends LibraryNode {
  @Nullable private VirtualFile myFile;

  public LibraryFileNode(@NotNull Project project, @NotNull NativeLibrary library, @NotNull ViewSettings settings) {
    super(project, library, settings);
    myFile = getFirstFile();
  }

  @Override
  @NotNull
  public Collection<? extends AbstractTreeNode> getChildren() {
    return Collections.emptyList();
  }

  @Override
  protected void update(PresentationData presentation) {
    presentation.setIcon(JavaClass);
    boolean hasDebugSymbols = myLibrary.hasDebugSymbols;
    SimpleTextAttributes attributes = hasDebugSymbols ? REGULAR_ATTRIBUTES : new SimpleTextAttributes(STYLE_WAVED, null, GRAY);
    presentation.addText(myLibrary.name, attributes);

    if (!hasDebugSymbols) {
      presentation.setTooltip("Library does not have debug symbols");
    }
  }

  @Override
  public boolean canNavigate() {
    return true;
  }

  @Override
  public void navigate(boolean requestFocus) {
    if (myFile == null) {
      myFile = getFirstFile();
    }
    if (myFile != null) {
      FileEditorManager.getInstance(myProject).openFile(myFile, requestFocus);
    }
  }

  @Override
  public boolean canRepresent(Object element) {
    if (element instanceof VirtualFile) {
      VirtualFile file = (VirtualFile)element;
      return myLibrary.files.contains(file);
    }
    return false;
  }

  @Override
  public int getWeight() {
    return 10;
  }

  @Override
  public int getTypeSortWeight(boolean sortByType) {
    return 1; // Show library nodes before folders.
  }

  @Override
  @Nullable
  public Comparable getSortKey() {
    return getTypeSortKey();
  }

  @Override
  @Nullable
  public Comparable getTypeSortKey() {
    return myLibrary.name;
  }

  @Override
  public boolean isAlwaysLeaf() {
    return true;
  }

  @Override
  public boolean isAlwaysShowPlus() {
    return false;
  }

  @Nullable
  @Override
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return myLibrary.name;
  }
}
