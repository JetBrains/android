/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.tools.idea.gradle.facet.NativeAndroidGradleFacet;
import com.google.common.collect.Lists;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ProjectViewModuleNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

import static org.jetbrains.android.facet.AndroidSourceType.JNI;

public class NativeAndroidModuleNode extends ProjectViewModuleNode {
  public NativeAndroidModuleNode(@NotNull Project project, @NotNull Module value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  @NotNull
  @Override
  public Collection<AbstractTreeNode> getChildren() {
    Module module = getValue();
    NativeAndroidGradleFacet facet = NativeAndroidGradleFacet.getInstance(module);
    if (facet == null || facet.getNativeAndroidGradleModel() == null) {
      return super.getChildren();
    }

    // Native modules have sources of a single type, i.e jni sources.
    List<AbstractTreeNode> nodes = Lists.newArrayListWithExpectedSize(1);
    nodes.add(new NativeAndroidSourceTypeNode(myProject, getValue(), getSettings(), JNI));
    return nodes;
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
    return String.format("%1$s (Native-Android-Gradle)", getValue().getName());
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    return super.equals(o);
  }
}
