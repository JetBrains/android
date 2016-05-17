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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.module.treeview;

import com.android.tools.idea.gradle.dsl.model.dependencies.DependencyModel;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.AbstractDependencyNode;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.AndroidArtifactNode;
import com.android.tools.idea.gradle.structure.configurables.ui.dependencies.PsDependencyComparator;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsModelNode;
import com.android.tools.idea.gradle.structure.model.PsDependency;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidArtifact;
import com.android.tools.idea.gradle.structure.model.android.PsLibraryAndroidDependency;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.intellij.util.containers.SortedList;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

final class DependencyNodes {
  private DependencyNodes() {
  }

  @NotNull
  static List<AbstractPsModelNode<?>> createNodesFor(@NotNull AndroidArtifactNode parent,
                                                     @NotNull Collection<PsDependency> dependencies) {
    List<AbstractPsModelNode<?>> children = Lists.newArrayList();

    List<PsDependency> declared = new SortedList<>(PsDependencyComparator.INSTANCE);
    Multimap<PsDependency, PsDependency> allTransitive = HashMultimap.create();
    List<PsDependency> mayBeTransitive = Lists.newArrayList();

    for (PsDependency dependency : dependencies) {
      Collection<DependencyModel> parsedModels = dependency.getParsedModels();
      if (parsedModels.isEmpty()) {
        mayBeTransitive.add(dependency);
      }
      else {
        for (DependencyModel parsedModel : parsedModels) {
          // In Android Libraries, the model will include artifacts declared in the "main" artifact in other artifacts as well. For example:
          //   compile 'com.android.support:appcompat-v7:23.0.1'
          // will be include as a dependency in "main", "android test" and "unit test" artifacts. Even though this is correct, it is
          // inconsistent with what Android App models return. In the case of Android Apps, 'appcompat' will be included only in the
          // "main" artifact.
          for (PsAndroidArtifact artifact : parent.getModels()) {
            if (artifact.contains(parsedModel)) {
              declared.add(dependency);
              break;
            }
          }
        }
        addTransitive(dependency, allTransitive);
      }
    }

    Collection<PsDependency> uniqueTransitives = allTransitive.values();
    declared.addAll(mayBeTransitive.stream()
                                   .filter(dependency -> !uniqueTransitives.contains(dependency))
                                   .collect(Collectors.toList()));

    for (PsDependency dependency : declared) {
      AbstractDependencyNode<?> child = AbstractDependencyNode.createNode(parent, dependency);
      if (child != null) {
        children.add(child);
      }
    }

    return children;
  }

  private static void addTransitive(@NotNull PsDependency dependency,
                                    @NotNull Multimap<PsDependency, PsDependency> allTransitive) {
    if (allTransitive.containsKey(dependency)) {
      return;
    }

    if (dependency instanceof PsLibraryAndroidDependency) {
      PsLibraryAndroidDependency libraryDependency = (PsLibraryAndroidDependency)dependency;
      ImmutableCollection<PsDependency> transitives = libraryDependency.getTransitiveDependencies();
      allTransitive.putAll(dependency, transitives);

      for (PsDependency transitive : transitives) {
        addTransitive(transitive, allTransitive);
      }
    }
  }
}
