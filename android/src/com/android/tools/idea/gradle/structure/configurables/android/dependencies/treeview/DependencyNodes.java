/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.idea.gradle.structure.configurables.ui.dependencies.PsDependencyComparator;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsModelNode;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsNode;
import com.android.tools.idea.gradle.structure.model.PsDependency;
import com.android.tools.idea.gradle.structure.model.android.*;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.util.containers.SortedList;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

import static com.android.builder.model.AndroidProject.ARTIFACT_MAIN;
import static java.util.stream.Collectors.toList;

public final class DependencyNodes {
  private DependencyNodes() {
  }

  @NotNull
  public static List<AbstractPsModelNode<?>> createNodesForResolvedDependencies(@NotNull AbstractPsNode parent,
                                                                                @NotNull PsAndroidArtifact artifact
  ) {
    return createNodesForResolvedDependencies(parent, artifact, Sets.newHashSet());
  }

  @NotNull
  public static List<AbstractPsModelNode<?>> createNodesForResolvedDependencies(@NotNull AbstractPsNode parent,
                                                                                @NotNull PsAndroidArtifact artifact,
                                                                                Set<String> allTransitive
  ) {
    PsAndroidArtifactDependencyCollection collection = artifact.getDependencies();
    List<AbstractPsModelNode<?>> children = Lists.newArrayList();

    List<PsDependency> declared = new SortedList<>(new PsDependencyComparator(parent.getUiSettings()));
    List<PsAndroidDependency> mayBeTransitive = Lists.newArrayList();

    if (!artifact.getResolvedName().equals(ARTIFACT_MAIN)) {
      PsVariant targetVariant = artifact.getParent();
      PsAndroidArtifact targetArtifact = targetVariant.findArtifact(ARTIFACT_MAIN);
      if (targetArtifact != null) {
        AndroidArtifactNode artifactNode = new AndroidArtifactNode(parent, targetArtifact);
        children.add(artifactNode);
      }
    }
    for (PsAndroidDependency dependency : collection.items()) {
      if (dependency.isDeclared()) {
        declared.add(dependency);
      }
      else {
        mayBeTransitive.add(dependency);
      }
      addTransitive(dependency, collection, allTransitive);
    }

    // Any other dependencies that are not declared, but somehow were not found as transitive.
    List<PsLibraryAndroidDependency> otherUnrecognised = mayBeTransitive
      .stream()
      .filter(it -> it instanceof PsLibraryAndroidDependency)
      .map(it -> (PsLibraryAndroidDependency)it)
      .filter(it -> !allTransitive.contains(it.getSpec().compactNotation()))
      .collect(toList());
    declared.addAll(otherUnrecognised);

    for (PsDependency dependency : declared) {
      AbstractDependencyNode<?> child = AbstractDependencyNode.createNode(parent, collection, dependency);
      if (child != null) {
        children.add(child);
      }
    }

    return children;
  }

  private static void addTransitive(@NotNull PsDependency dependency,
                                    @NotNull PsAndroidDependencyCollection collection,
                                    @NotNull Set<String> allTransitive) {
    if (dependency instanceof PsLibraryAndroidDependency) {
      PsLibraryAndroidDependency libraryDependency = (PsLibraryAndroidDependency)dependency;

      for (PsLibraryAndroidDependency transitive : libraryDependency.getTransitiveDependencies(collection)) {
        if (allTransitive.add(transitive.getSpec().compactNotation())) {
          addTransitive(transitive, collection, allTransitive);
        }
      }
    }
  }
}
