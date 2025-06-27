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
package com.android.tools.idea.navigator.nodes.other;

import com.android.tools.idea.navigator.nodes.AndroidViewModuleNode;
import com.google.common.collect.Sets;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.ui.Queryable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NonAndroidModuleNode extends AndroidViewModuleNode {
  public NonAndroidModuleNode(
    @NotNull Project project,
    @NotNull Module value,
    @NotNull ViewSettings settings
  ) {
    super(project, value, settings);
  }

  @Override
  @NotNull
  protected Collection<AbstractTreeNode<?>> getModuleChildren() {
    Module module = getModule();
    Set<NonAndroidSourceType> sourceTypes = getNonEmptySourceTypes(module);
    List<AbstractTreeNode<?>> nodes = new ArrayList<>(sourceTypes.size());

    assert myProject != null;
    for (NonAndroidSourceType type : sourceTypes) {
      nodes.add(new NonAndroidSourceTypeNode(myProject, module, getSettings(), type));
    }

    return nodes;
  }

  @NotNull
  private static Set<NonAndroidSourceType> getNonEmptySourceTypes(@NotNull Module module) {
    ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    Set<NonAndroidSourceType> sourceTypes = Sets.newHashSetWithExpectedSize(NonAndroidSourceType.values().length);

    ContentEntry[] contentEntries = rootManager.getContentEntries();
    for (ContentEntry entry : contentEntries) {
      for (NonAndroidSourceType type : NonAndroidSourceType.values()) {
        for (SourceFolder sourceFolder : entry.getSourceFolders(type.rootType)) {
          if (sourceFolder.getFile() != null) {
            sourceTypes.add(type);
            break;
          }
        }
      }
    }

    return sourceTypes;
  }

  @Override
  @Nullable
  public Comparable getSortKey() {
    return getModule().getName();
  }

  @Override
  @Nullable
  public Comparable getTypeSortKey() {
    return getSortKey();
  }

  @Override
  @Nullable
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    Module module = getModule();
    return String.format("%1$s (non-Android)", super.toTestString(printInfo));
  }

  @NotNull
  private Module getModule() {
    Module module = getValue();
    assert module != null;
    return module;
  }
}
