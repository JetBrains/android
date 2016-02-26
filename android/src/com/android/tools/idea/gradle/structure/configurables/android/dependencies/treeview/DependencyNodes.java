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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview;

import com.android.tools.idea.gradle.structure.configurables.android.dependencies.PsdAndroidDependencyModelComparator;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsdNode;
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidDependencyModel;
import com.android.tools.idea.gradle.structure.model.android.PsdLibraryDependencyModel;
import com.android.tools.idea.gradle.structure.model.android.PsdModuleDependencyModel;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.intellij.util.containers.SortedList;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

final class DependencyNodes {
  private DependencyNodes() {
  }

  @NotNull
  static List<AbstractPsdNode<?>> createNodesFor(@NotNull AbstractPsdNode parent,
                                                 @NotNull Collection<PsdAndroidDependencyModel> dependencies) {
    List<AbstractPsdNode<?>> children = Lists.newArrayList();

    List<PsdAndroidDependencyModel> declared = new SortedList<PsdAndroidDependencyModel>(PsdAndroidDependencyModelComparator.INSTANCE);
    Multimap<PsdAndroidDependencyModel, PsdAndroidDependencyModel> allTransitive = HashMultimap.create();
    List<PsdAndroidDependencyModel> mayBeTransitive = Lists.newArrayList();

    for (PsdAndroidDependencyModel dependency : dependencies) {
      if (dependency.isEditable()) {
        declared.add(dependency);
        addTransitive(dependency, allTransitive);
      }
      else {
        mayBeTransitive.add(dependency);
      }
    }

    Collection<PsdAndroidDependencyModel> uniqueTransitives = allTransitive.values();
    for (PsdAndroidDependencyModel dependency : mayBeTransitive) {
      if (!uniqueTransitives.contains(dependency)) {
        declared.add(dependency);
      }
    }

    for (PsdAndroidDependencyModel dependency : declared) {
      if (dependency instanceof PsdLibraryDependencyModel) {
        children.add(new LibraryDependencyNode(parent, (PsdLibraryDependencyModel)dependency));
      }
      else if (dependency instanceof PsdModuleDependencyModel) {
        children.add(new ModuleDependencyNode(parent, (PsdModuleDependencyModel)dependency));
      }
    }

    return children;
  }

  private static void addTransitive(@NotNull PsdAndroidDependencyModel dependency,
                                    @NotNull Multimap<PsdAndroidDependencyModel, PsdAndroidDependencyModel> allTransitive) {
    if (allTransitive.containsKey(dependency)) {
      return;
    }

    if (dependency instanceof PsdLibraryDependencyModel) {
      Collection<PsdAndroidDependencyModel> transitives = ((PsdLibraryDependencyModel)dependency).getTransitiveDependencies();
      allTransitive.putAll(dependency, transitives);

      for (PsdAndroidDependencyModel transitive : transitives) {
        addTransitive(transitive, allTransitive);
      }
    }
  }
}
