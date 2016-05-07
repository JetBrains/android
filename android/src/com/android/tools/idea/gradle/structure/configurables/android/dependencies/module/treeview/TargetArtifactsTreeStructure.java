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
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.PsRootNode;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractBaseTreeStructure;
import com.android.tools.idea.gradle.structure.model.PsModelNameComparator;
import com.android.tools.idea.gradle.structure.model.android.*;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.android.builder.model.AndroidProject.ARTIFACT_MAIN;

public class TargetArtifactsTreeStructure extends AbstractBaseTreeStructure {
  @NotNull private final PsAndroidModule myModule;
  @NotNull private final PsRootNode<TargetVariantNode> myRootNode = new PsRootNode<>();

  TargetArtifactsTreeStructure(@NotNull PsAndroidModule module) {
    myModule = module;
  }

  @Override
  public Object getRootElement() {
    return myRootNode;
  }

  void displayTargetArtifacts(@Nullable PsAndroidDependency dependency) {
    if (dependency == null) {
      myRootNode.setChildren(Collections.emptyList());
      return;
    }

    ImmutableCollection<DependencyModel> parsedDependencies = dependency.getParsedModels();
    Multimap<PsVariant, PsAndroidArtifact> artifactsByVariant = HashMultimap.create();

    for (PsDependencyContainer container : dependency.getContainers()) {
      PsAndroidArtifact foundArtifact = container.findArtifact(myModule, false);
      if (foundArtifact != null && foundArtifact.containsAny(parsedDependencies)) {
        PsVariant variant = foundArtifact.getParent();
        artifactsByVariant.put(variant, foundArtifact);

        String name = foundArtifact.getResolvedName();
        if (name.equals(ARTIFACT_MAIN)) {
          variant.forEachArtifact(artifact -> artifactsByVariant.put(variant, artifact));
        }
      }
    }

    List<PsVariant> variants = artifactsByVariant.keySet().stream().collect(Collectors.toList());
    if (variants.size() > 1) {
      Collections.sort(variants, new PsModelNameComparator<>());
    }

    List<TargetVariantNode> children = Lists.newArrayList();
    for (PsVariant variant : variants) {
      TargetVariantNode variantNode = new TargetVariantNode(variant);

      Collection<PsAndroidArtifact> artifacts = artifactsByVariant.get(variant);
      List<PsAndroidArtifact> sorted = artifacts.stream().collect(Collectors.toList());
      if (sorted.size() > 1) {
        Collections.sort(sorted, ArtifactComparator.INSTANCE);
      }

      List<TargetArtifactNode> artifactNodes = Lists.newArrayList();
      for (PsAndroidArtifact artifact : sorted) {
        artifactNodes.add(new TargetArtifactNode(artifact));
      }
      variantNode.setChildren(artifactNodes);

      children.add(variantNode);
    }
    myRootNode.setChildren(children);
  }
}
