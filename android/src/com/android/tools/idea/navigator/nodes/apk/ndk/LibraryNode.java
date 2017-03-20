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

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

import static com.intellij.icons.AllIcons.FileTypes.JavaClass;

public class LibraryNode extends ProjectViewNode<VirtualFile> {
  @NotNull private final VirtualFile myFile;

  public LibraryNode(@NotNull Project project, @NotNull VirtualFile file, @NotNull ViewSettings settings) {
    super(project, file, settings);
    myFile = file;
  }

  @Override
  @NotNull
  public Collection<? extends AbstractTreeNode> getChildren() {
    return Collections.emptyList();
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return false;
  }

  @Override
  protected void update(PresentationData presentation) {
    presentation.setIcon(JavaClass);
    presentation.setPresentableText(myFile.getName());
  }

  @Nullable
  @Override
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return myFile.getName();
  }
}
