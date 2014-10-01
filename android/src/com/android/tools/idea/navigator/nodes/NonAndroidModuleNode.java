/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.navigator.nodes;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ProjectViewModuleNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.ui.Queryable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class NonAndroidModuleNode extends ProjectViewModuleNode {
  public NonAndroidModuleNode(Project project, Module value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  @NotNull
  @Override
  public Collection<AbstractTreeNode> getChildren() {
    Set<NonAndroidSourceType> sourceTypes = getNonEmptySourceTypes(getValue());
    List<AbstractTreeNode> nodes = Lists.newArrayListWithExpectedSize(sourceTypes.size());

    for (NonAndroidSourceType type : sourceTypes) {
      nodes.add(new NonAndroidSourceTypeNode(myProject, getValue(), getSettings(), type));
    }

    return nodes;
  }

  private static Set<NonAndroidSourceType> getNonEmptySourceTypes(Module module) {
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

  @Nullable
  @Override
  public Comparable getSortKey() {
    return getValue().getName();
  }

  @Nullable
  @Override
  public Comparable getTypeSortKey() {
    return getSortKey();
  }

  @Nullable
  @Override
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return String.format("%1$s (non-Android)", getValue().getName());
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    return super.equals(o);
  }
}
