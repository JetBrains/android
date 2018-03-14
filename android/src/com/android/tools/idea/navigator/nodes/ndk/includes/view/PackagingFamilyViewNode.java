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

import com.android.tools.idea.navigator.nodes.ndk.includes.model.ClassifiedIncludeValue;
import com.android.tools.idea.navigator.nodes.ndk.includes.model.PackageFamilyValue;
import com.android.tools.idea.navigator.nodes.ndk.includes.utils.IncludeSet;
import com.android.tools.idea.navigator.nodes.ndk.includes.utils.LexicalIncludePaths;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.intellij.openapi.util.io.FileUtil.getLocationRelativeToUserHome;
import static com.intellij.ui.SimpleTextAttributes.GRAY_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;

/**
 * Presents a view over PackagingFamilyExpression. For example,
 *
 * NDK <- this class
 * STL <- an NDK module
 * Native App Glue <- another module
 */
public class PackagingFamilyViewNode extends IncludeViewNode<PackageFamilyValue> {
  protected PackagingFamilyViewNode(@NotNull IncludeSet includeFolders,
                                    @Nullable Project project,
                                    @NotNull PackageFamilyValue include,
                                    @NotNull ViewSettings viewSettings,
                                    boolean showPackageType) {
    super(include, includeFolders, showPackageType, project, viewSettings);
  }

  @NotNull
  private PackageFamilyValue getPackageFamilyValue() {
    PackageFamilyValue value = getValue();
    assert value != null;
    return value;
  }

  @NotNull
  @Override
  public Collection<? extends AbstractTreeNode> getChildren() {
    List<AbstractTreeNode> children = new ArrayList<>();
    PackageFamilyValue value = getPackageFamilyValue();
    for (ClassifiedIncludeValue child : value.myIncludes) {
      children.add(createIncludeView(child, myIncludeFolders, false, getProject(), getSettings()));
    }
    return children;
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
    presentation.addText(getPackageFamilyValue().getPackageType().myDescription, REGULAR_ATTRIBUTES);
    presentation
      .addText(String.format(" (%s)", getLocationRelativeToUserHome(getPackageFamilyValue().getPackageFamilyBaseFolder().getPath())),
               GRAY_ATTRIBUTES);
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    if (!LexicalIncludePaths.hasHeaderExtension(file.getName())) {
      return false;
    }
    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    PackageFamilyValue value = getPackageFamilyValue();
    for (ClassifiedIncludeValue include : value.myIncludes) {
      VirtualFile ancestor = fileSystem.findFileByIoFile(include.getPackageFamilyBaseFolder());
      if (ancestor != null && VfsUtilCore.isAncestor(ancestor, file, false)) {
        return true;
      }
    }
    return false;
  }
}
