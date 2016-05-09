/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.resolved;

import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.AbstractDependencyNode;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.LibraryDependencyNode;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.ModuleDependencyNode;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsModelNode;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.intellij.util.containers.ContainerUtil.getFirstItem;

public class NodeSelectionDetector {
  @NotNull private final Map<String, List<AbstractDependencyNode<? extends PsAndroidDependency>>> mySelection = Maps.newHashMap();

  public void add(@NotNull AbstractPsModelNode node) {
    String key = null;
    if (node instanceof ModuleDependencyNode) {
      key = ((ModuleDependencyNode)node).getModels().get(0).getGradlePath();
    }
    if (node instanceof LibraryDependencyNode) {
      key = ((LibraryDependencyNode)node).getModels().get(0).getResolvedSpec().toString();
    }
    if (key != null) {
      List<AbstractDependencyNode<? extends PsAndroidDependency>> nodes = mySelection.get(key);
      if (nodes == null) {
        nodes = Lists.newArrayList();
        mySelection.put(key, nodes);
      }
      nodes.add(((AbstractDependencyNode<? extends PsAndroidDependency>)node));
    }
  }

  @NotNull
  public List<AbstractDependencyNode<? extends PsAndroidDependency>> getSingleTypeSelection() {
    Set<String> keys = mySelection.keySet();
    if (keys.size() == 1) {
      // Only notify selection if all the selected nodes refer to the same dependency.
      return mySelection.get(getFirstItem(keys));
    }
    return Collections.emptyList();
  }
}
