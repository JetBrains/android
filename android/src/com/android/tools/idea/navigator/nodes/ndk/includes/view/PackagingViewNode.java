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


import static com.intellij.ui.SimpleTextAttributes.GRAY_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;

import com.android.tools.idea.navigator.nodes.ndk.includes.model.PackageValue;
import com.android.tools.idea.navigator.nodes.ndk.includes.model.SimpleIncludeValue;
import com.android.tools.idea.navigator.nodes.ndk.includes.utils.LexicalIncludePaths;
import com.android.tools.idea.navigator.nodes.ndk.includes.utils.PresentationDataWrapper;
import com.google.common.collect.ImmutableList;
import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Presents a view over {@link PackageValue}. See the class doc of {@link PackageValue} for more details. */
public class PackagingViewNode extends IncludeViewNode<PackageValue> {
  protected PackagingViewNode(@NotNull Collection<File> includeFolders,
                              @Nullable Project project,
                              @NotNull PackageValue dependency,
                              @NotNull ViewSettings viewSettings,
                              boolean showPackageType) {
    super(dependency, includeFolders, showPackageType, project, viewSettings);
  }

  @NotNull
  private PackageValue getPackageValue() {
    PackageValue value = getValue();
    assert value != null;
    return value;
  }

  @NotNull
  @Override
  public Collection<? extends AbstractTreeNode<?>> getChildren() {
    Project project = getProject();
    if (project == null) {
      //noinspection unchecked
      return Collections.EMPTY_LIST;
    }
    List<File> folders = new ArrayList<>();
    List<SimpleIncludeValue> result = new ArrayList<>();
    PackageValue value = getPackageValue();
    for (File folder : myIncludeFolders) {
      for (SimpleIncludeValue simpleIncludeValue : value.getIncludes()) {
        if (FileUtil.filesEqual(simpleIncludeValue.getIncludeFolder(), folder)) {
          result.add(simpleIncludeValue);
        }
      }
    }

    for (SimpleIncludeValue child : result) {
      folders.add(child.getIncludeFolder());
    }

    return IncludeViewNodes.getIncludeFolderNodesWithShadowing(folders, ImmutableList.of(), true, project, getSettings());
  }

  @Override
  protected void writeDescription(@NotNull PresentationDataWrapper presentation) {
    presentation.setIcon(AllIcons.Nodes.Module);
    PackageValue value = getPackageValue();
    presentation.addText(value.getSimplePackageName(), REGULAR_ATTRIBUTES);
    if (myShowPackageType) {
      presentation.addText(String.format(" (%s, ", getPackageValue().getPackageType().myDescription), GRAY_ATTRIBUTES);
    }
    else {
      presentation.addText(" (", GRAY_ATTRIBUTES);
    }
    presentation.addText(String.format("%s)", getPackageValue().getDescriptiveText()), GRAY_ATTRIBUTES);
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    if (!LexicalIncludePaths.hasHeaderExtension(file.getName())) {
      return false;
    }
    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    PackageValue value = getPackageValue();
    for (SimpleIncludeValue include : value.getIncludes()) {
      VirtualFile ancestor = fileSystem.findFileByIoFile(include.getIncludeFolder());
      if (ancestor != null && VfsUtilCore.isAncestor(ancestor, file, false)) {
        return true;
      }
    }
    return false;
  }
}
