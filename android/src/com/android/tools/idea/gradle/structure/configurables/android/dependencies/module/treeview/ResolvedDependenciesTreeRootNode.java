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
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.ArtifactComparator;
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings;
import com.android.tools.idea.gradle.structure.configurables.ui.dependencies.PsDependencyComparator;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsModelNode;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsResettableNode;
import com.android.tools.idea.gradle.structure.model.PsDependency;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidArtifact;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.android.tools.idea.gradle.structure.model.android.PsDependencyContainer;
import com.android.tools.idea.gradle.structure.model.android.PsVariant;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.util.containers.SortedList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.android.builder.model.AndroidProject.ARTIFACT_MAIN;
import static com.android.tools.idea.gradle.structure.configurables.android.dependencies.module.treeview.DependencyNodes.createNodesFor;

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

    // [Outer map] key: variant name, value: dependencies by artifact
    // [Inner map] key: artifact name, value: dependencies
    Map<String, Map<String, List<PsDependency>>> dependenciesByVariantAndArtifact = Maps.newHashMap();

    module.forEachDependency(dependency -> {
      if (!dependency.isDeclared()) {
        return; // Only show "declared" dependencies as top-level dependencies.
      }
      for (PsDependencyContainer container : dependency.getContainers()) {
        Map<String, List<PsDependency>> dependenciesByArtifact =
          dependenciesByVariantAndArtifact.get(container.getVariant());

        if (dependenciesByArtifact == null) {
          dependenciesByArtifact = Maps.newHashMap();
          dependenciesByVariantAndArtifact.put(container.getVariant(), dependenciesByArtifact);
        }

        List<PsDependency> artifactDependencies = dependenciesByArtifact.get(container.getArtifact());
        if (artifactDependencies == null) {
          artifactDependencies = Lists.newArrayList();
          dependenciesByArtifact.put(container.getArtifact(), artifactDependencies);
        }

        artifactDependencies.add(dependency);
      }
    });

    List<String> variantNames = Lists.newArrayList(dependenciesByVariantAndArtifact.keySet());
    Collections.sort(variantNames);

    for (String variantName : variantNames) {
      PsVariant variant = variantsByName.get(variantName);

      Map<String, List<PsDependency>> dependenciesByArtifact = dependenciesByVariantAndArtifact.get(variantName);

      if (dependenciesByArtifact != null) {
        List<String> artifactNames = Lists.newArrayList(dependenciesByArtifact.keySet());
        //noinspection TestOnlyProblems
        Collections.sort(artifactNames, ArtifactComparator.byName());

        for (String artifactName : artifactNames) {
          PsAndroidArtifact artifact = variant.findArtifact(artifactName);
          assert artifact != null;

          AndroidArtifactNode mainArtifactNode = null;
          String mainArtifactName = ARTIFACT_MAIN;
          if (!mainArtifactName.equals(artifactName)) {
            // Add "main" artifact as a dependency of "unit test" or "android test" artifact.
            PsAndroidArtifact mainArtifact = variant.findArtifact(mainArtifactName);
            if (mainArtifact != null) {
              List<PsDependency> artifactDependencies = dependenciesByArtifact.get(mainArtifactName);
              if (artifactDependencies == null) {
                artifactDependencies = Collections.emptyList();
              }
              mainArtifactNode = createArtifactNode(mainArtifact, artifactDependencies, null);
            }
          }

          AndroidArtifactNode artifactNode = createArtifactNode(artifact, dependenciesByArtifact.get(artifactName), mainArtifactNode);
          if (artifactNode != null) {
            childrenNodes.add(artifactNode);
          }
        }
      }
    }

    return childrenNodes;
  }

  @Nullable
  private AndroidArtifactNode createArtifactNode(@NotNull PsAndroidArtifact artifact,
                                                 @NotNull List<PsDependency> dependencies,
                                                 @Nullable AndroidArtifactNode mainArtifactNode) {
    if (!dependencies.isEmpty() || mainArtifactNode != null) {
      AndroidArtifactNode artifactNode = new AndroidArtifactNode(this, artifact);
      populate(artifactNode, dependencies, mainArtifactNode, getUiSettings());
      return artifactNode;
    }
    return null;
  }

  private static void populate(@NotNull AndroidArtifactNode artifactNode,
                               @NotNull List<PsDependency> dependencies,
                               @Nullable AndroidArtifactNode mainArtifactNode,
                               @NotNull PsUISettings uiSettings) {
    List<AbstractPsModelNode<?>> children = createNodesFor(artifactNode, dependencies, uiSettings);
    if (mainArtifactNode != null) {
      children.add(0, mainArtifactNode);
    }
    artifactNode.setChildren(children);
  }

}
