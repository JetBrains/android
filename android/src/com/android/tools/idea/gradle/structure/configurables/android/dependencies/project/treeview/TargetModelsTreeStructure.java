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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.project.treeview;

import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.AbstractBaseTreeStructure;
import com.android.tools.idea.gradle.structure.model.PsProject;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class TargetModelsTreeStructure extends AbstractBaseTreeStructure {
  @NotNull private final TargetModelsTreeRootNode myRootNode;

  TargetModelsTreeStructure(@NotNull PsProject project) {
    myRootNode = new TargetModelsTreeRootNode(project);
  }

  @Override
  @NotNull
  public Object getRootElement() {
    return myRootNode;
  }

  void displayTargetModules(@NotNull List<? extends PsAndroidDependency> dependencies) {
    Map<String, PsAndroidModule> modules = Maps.newHashMap();

    List<TargetAndroidModuleNode> children = Lists.newArrayList();
    for (PsAndroidDependency dependency : dependencies) {
      PsAndroidModule module = dependency.getParent();
      modules.put(module.getName(), module);
    }

    for (PsAndroidModule module : modules.values()) {
      TargetAndroidModuleNode node = new TargetAndroidModuleNode(myRootNode, module);
      children.add(node);
    }

    Collections.sort(children, new Comparator<TargetAndroidModuleNode>() {
      @Override
      public int compare(TargetAndroidModuleNode m1, TargetAndroidModuleNode m2) {
        return m1.getName().compareTo(m2.getName());
      }
    });
    myRootNode.setChildren(children);
  }

  @Override
  public void commit() {
  }

  @Override
  public boolean hasSomethingToCommit() {
    return false;
  }
}
