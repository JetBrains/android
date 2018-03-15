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

import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.AndroidArtifactNode;
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsModelNode;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsResettableNode;
import com.android.tools.idea.gradle.structure.model.android.*;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.android.builder.model.AndroidProject.ARTIFACT_MAIN;
import static com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.DependencyNodes.createNodesForResolvedDependencies;

class ResolvedDependenciesTreeRootNode extends AbstractPsResettableNode<PsAndroidModule> {

  ResolvedDependenciesTreeRootNode(@NotNull PsAndroidModule module, @NotNull PsUISettings uiSettings) {
    super(module, uiSettings);
  }

  @Override
  @NotNull
  protected List<? extends AbstractPsModelNode> createChildren() {
    Map<String, PsVariant> variantsByName = Maps.newHashMap();
    for (PsAndroidModule module : getModels()) {
      module.forEachVariant(variant -> variantsByName.put(variant.getName(), variant));
    }

    PsAndroidModule androidModule = getModels().get(0);
    return createChildren(androidModule, variantsByName);
  }

  @NotNull
  private List<? extends AndroidArtifactNode> createChildren(@NotNull PsAndroidModule module,
                                                             @NotNull Map<String, PsVariant> variantsByName) {
    List<AndroidArtifactNode> childrenNodes = Lists.newArrayList();
    List<String> variantNames = new ArrayList<>(variantsByName.keySet());
    Collections.sort(variantNames);

    for (String variantName : variantNames) {
      PsVariant variant = variantsByName.get(variantName);

      Map<String, PsAndroidArtifact> artifacts = Maps.newLinkedHashMap();
      variant.forEachArtifact(artifact -> artifacts.put(artifact.getResolvedName(), artifact));
      List<String> artifactNames = new ArrayList<>(artifacts.keySet());
      //noinspection TestOnlyProblems
      Collections.sort(artifactNames);

      for (String artifactName : artifactNames) {
        PsAndroidArtifact artifact = variant.findArtifact(artifactName);
        assert artifact != null;

        AndroidArtifactNode mainArtifactNode = null;
        String mainArtifactName = ARTIFACT_MAIN;
        if (!mainArtifactName.equals(artifactName)) {
          // Add "main" artifact as a dependency of "unit test" or "android test" artifact.
          PsAndroidArtifact mainArtifact = variant.findArtifact(mainArtifactName);
          if (mainArtifact != null) {
            mainArtifactNode = createArtifactNode(mainArtifact, null);
          }
        }

        AndroidArtifactNode artifactNode = createArtifactNode(artifact, mainArtifactNode);
        if (artifactNode != null) {
          childrenNodes.add(artifactNode);
        }
      }
    }

    return childrenNodes;
  }

  @Nullable
  private AndroidArtifactNode createArtifactNode(@NotNull PsAndroidArtifact artifact,
                                                 @Nullable AndroidArtifactNode mainArtifactNode) {
    PsAndroidDependencyCollection collection = new PsAndroidArtifactDependencyCollection(artifact);
    List<PsAndroidDependency> dependencies = collection.items();
    if (!dependencies.isEmpty() || mainArtifactNode != null) {
      AndroidArtifactNode artifactNode = new AndroidArtifactNode(this, artifact);
      populate(artifactNode, collection, dependencies, mainArtifactNode, getUiSettings());
      return artifactNode;
    }
    return null;
  }

  private static void populate(@NotNull AndroidArtifactNode artifactNode,
                               @NotNull PsAndroidDependencyCollection collection,
                               @NotNull List<PsAndroidDependency> dependencies,
                               @Nullable AndroidArtifactNode mainArtifactNode,
                               @NotNull PsUISettings uiSettings) {
    List<AbstractPsModelNode<?>> children = createNodesForResolvedDependencies(artifactNode, collection, dependencies, uiSettings);
    if (mainArtifactNode != null) {
      children.add(0, mainArtifactNode);
    }
    artifactNode.setChildren(children);
  }

}
