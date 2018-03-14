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

import com.android.tools.idea.navigator.nodes.ndk.includes.model.PackageType;
import com.android.tools.idea.navigator.nodes.ndk.includes.model.SimpleIncludeValue;
import com.android.tools.idea.navigator.nodes.ndk.includes.utils.IncludeSet;
import com.android.tools.idea.navigator.nodes.ndk.includes.utils.LexicalIncludePaths;
import com.google.common.collect.ImmutableList;
import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

import static com.intellij.openapi.util.io.FileUtil.getLocationRelativeToUserHome;
import static com.intellij.ui.SimpleTextAttributes.GRAY_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;

/**
 * A view class over SimpleIncludeExpressions. This is a basic single include folder.
 */
public class SimpleIncludeViewNode extends IncludeViewNode<SimpleIncludeValue> {

  protected SimpleIncludeViewNode(@NotNull SimpleIncludeValue thisInclude,
                                  @NotNull IncludeSet allIncludes,
                                  boolean showPackageType,
                                  @Nullable Project project,
                                  @NotNull ViewSettings viewSettings) {
    super(thisInclude, allIncludes, showPackageType, project, viewSettings);
  }

  @NotNull
  private SimpleIncludeValue getSimpleIncludeValue() {
    SimpleIncludeValue value = getValue();
    assert value != null;
    return value;
  }

  @NotNull
  @Override
  public Collection<? extends AbstractTreeNode> getChildren() {
    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    Project project = getProject();
    if (project == null) {
      //noinspection unchecked
      return Collections.EMPTY_LIST;
    }
    PsiManager psiManager = PsiManager.getInstance(project);
    SimpleIncludeValue value = getSimpleIncludeValue();
    VirtualFile virtualFile = fileSystem.findFileByIoFile(value.myIncludeFolder);
    if (virtualFile == null) {
      //noinspection unchecked
      return Collections.EMPTY_LIST;
    }
    PsiDirectory directory = psiManager.findDirectory(virtualFile);
    if (directory == null) {
      //noinspection unchecked
      return Collections.EMPTY_LIST;
    }
    return new PsiIncludeDirectoryView(getProject(), ImmutableList.of(), false, directory, getSettings()).getChildren();
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
    SimpleIncludeValue concrete = getSimpleIncludeValue();
    if (concrete.getPackageType() == PackageType.IncludeFolder) {
      presentation.setIcon(AllIcons.Nodes.Folder);
    }
    else {
      presentation.setIcon(AllIcons.Nodes.JavaModuleRoot);
    }
    presentation.addText(concrete.mySimplePackageName, REGULAR_ATTRIBUTES);
    if (myShowPackageType && concrete.getPackageType() != PackageType.IncludeFolder) {
      presentation.addText(String.format(" (%s, ", concrete.getPackageType().myDescription), GRAY_ATTRIBUTES);
    }
    else {
      presentation.addText(" (", GRAY_ATTRIBUTES);
    }
    if (concrete.getPackageType() == PackageType.IncludeFolder) {
      presentation.addText(String.format("%s)", getLocationRelativeToUserHome(concrete.myIncludeFolder.getPath())),
                           GRAY_ATTRIBUTES);
    }
    else {
      presentation
        .addText(String.format("%s)", LexicalIncludePaths.trimPathSeparators(concrete.myRelativeIncludeSubFolder)), GRAY_ATTRIBUTES);
    }
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    if (!LexicalIncludePaths.hasHeaderExtension(file.getName())) {
      return false;
    }
    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    SimpleIncludeValue value = getSimpleIncludeValue();
    VirtualFile ancestor = fileSystem.findFileByIoFile(value.myIncludeFolder);
    if (ancestor != null && VfsUtilCore.isAncestor(ancestor, file, false)) {
      return true;
    }
    return false;
  }

  @NotNull
  @Override
  public Comparable getSortKey() {
    SimpleIncludeValue value = getSimpleIncludeValue();
    if (value.getPackageType() != PackageType.IncludeFolder) {
      return "[icon-f]" + value.getSortKey();
    }
    return "[icon-m]" + value.getSortKey();
  }
}
