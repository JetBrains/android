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

import com.android.tools.idea.gradle.structure.configurables.android.treeview.AbstractRootNode;
import com.android.tools.idea.gradle.structure.configurables.ui.PsdUISettings;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsdNode;
import com.android.tools.idea.gradle.structure.model.android.*;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.android.builder.model.AndroidProject.ARTIFACT_MAIN;
import static com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.DependencyNodes.createNodesFor;

class RootNode extends AbstractRootNode {
  private boolean myGroupVariants = PsdUISettings.getInstance().VARIANTS_DEPENDENCIES_GROUP_VARIANTS;

  RootNode(@NotNull PsdAndroidModuleModel moduleModel) {
    super(moduleModel);
  }

  boolean settingsChanged() {
    if (PsdUISettings.getInstance().VARIANTS_DEPENDENCIES_GROUP_VARIANTS != myGroupVariants) {
      // If the "Group Variants" setting changed, remove all children nodes, so the subsequent call to "queueUpdate" will recreate them.
      myGroupVariants = PsdUISettings.getInstance().VARIANTS_DEPENDENCIES_GROUP_VARIANTS;
      removeChildren();
      return true;
    }
    return false;
  }

  @Override
  @NotNull
  protected List<? extends AbstractPsdNode> createChildren(@NotNull Collection<PsdVariantModel> variantModels) {
    List<VariantNode> variantNodes = Lists.newArrayList();

    Map<String, PsdVariantModel> variantsByName = Maps.newHashMap();
    for (PsdVariantModel variantModel : variantModels) {
      variantsByName.put(variantModel.getName(), variantModel);
    }

    List<PsdAndroidDependencyModel> dependencies = getModels().get(0).getDependencies();

    if (myGroupVariants) {
      VariantComparator variantComparator = new VariantComparator();
      Map<List<String>, List<PsdAndroidDependencyModel>> groups = groupVariants(dependencies);

      for (List<String> variantNames : groups.keySet()) {
        List<PsdVariantModel> groupVariants = Lists.newArrayList();
        for (String variantName : variantNames) {
          PsdVariantModel found = variantsByName.get(variantName);
          assert found != null;
          groupVariants.add(found);
        }
        Collections.sort(groupVariants, variantComparator);
        VariantNode variantNode = new VariantNode(this, groupVariants);
        List<PsdAndroidDependencyModel> variantDependencies = groups.get(variantNames);
        variantNode.setChildren(variantDependencies);

        variantNodes.add(variantNode);
      }
    }
    else {
      return createChildren(dependencies, variantsByName);
    }

    return variantNodes;
  }

  @NotNull
  private List<? extends ArtifactNode> createChildren(@NotNull List<PsdAndroidDependencyModel> dependencies,
                                                      @NotNull Map<String, PsdVariantModel> variantsByName) {
    List<ArtifactNode> childrenNodes = Lists.newArrayList();

    // [Outer map] key: variant name, value: dependencies by artifact
    // [Inner map] key: artifact name, value: dependencies
    Map<String, Map<String, List<PsdAndroidDependencyModel>>> dependenciesByVariantAndArtifact = Maps.newHashMap();
    for (PsdAndroidDependencyModel dependency : dependencies) {
      if (!dependency.isEditable()) {
        continue; // Only show "declared" dependencies as top-level dependencies.
      }
      for (PsdDependencyContainer container : dependency.getContainers()) {
        Map<String, List<PsdAndroidDependencyModel>> dependenciesByArtifact =
          dependenciesByVariantAndArtifact.get(container.getVariant());

        if (dependenciesByArtifact == null) {
          dependenciesByArtifact = Maps.newHashMap();
          dependenciesByVariantAndArtifact.put(container.getVariant(), dependenciesByArtifact);
        }

        List<PsdAndroidDependencyModel> dependencyModels = dependenciesByArtifact.get(container.getArtifact());
        if (dependencyModels == null) {
          dependencyModels = Lists.newArrayList();
          dependenciesByArtifact.put(container.getArtifact(), dependencyModels);
        }

        dependencyModels.add(dependency);
      }
    }

    List<String> variantNames = Lists.newArrayList(dependenciesByVariantAndArtifact.keySet());
    Collections.sort(variantNames);

    for (String variantName : variantNames) {
      PsdVariantModel variantModel = variantsByName.get(variantName);

      // VariantNode variantNode = new VariantNode(this, variantModel);

      Map<String, List<PsdAndroidDependencyModel>> dependenciesByArtifact = dependenciesByVariantAndArtifact.get(variantName);

      if (dependenciesByArtifact != null) {
        List<String> artifactNames = Lists.newArrayList(dependenciesByArtifact.keySet());
        Collections.sort(artifactNames, new ArtifactNameComparator());

        for (String artifactName : artifactNames) {
          PsdAndroidArtifactModel artifactModel = variantModel.findArtifact(artifactName);
          assert artifactModel != null;
          List<PsdAndroidDependencyModel> artifactDependencies = dependenciesByArtifact.get(artifactName);

          if (!artifactDependencies.isEmpty()) {
            ArtifactNode artifactNode = new ArtifactNode(this, artifactModel);
            artifactNode.setChildren(createNodesFor(artifactNode, artifactDependencies));
            childrenNodes.add(artifactNode);
          }
        }
      }
    }

    return childrenNodes;
  }

  @VisibleForTesting
  @NotNull
  static Map<List<String>, List<PsdAndroidDependencyModel>> groupVariants(List<PsdAndroidDependencyModel> dependencies) {
    Map<String, List<PsdAndroidDependencyModel>> dependenciesByVariant = Maps.newHashMap();
    for (PsdAndroidDependencyModel dependency : dependencies) {
      List<String> variants = dependency.getVariants();
      for (String variant : variants) {
        List<PsdAndroidDependencyModel> variantDependencies = dependenciesByVariant.get(variant);
        if (variantDependencies == null) {
          variantDependencies = Lists.newArrayList();
          dependenciesByVariant.put(variant, variantDependencies);
        }
        variantDependencies.add(dependency);
      }
    }

    List<List<String>> variantGroups = Lists.newArrayList();
    List<String> variants = Lists.newArrayList(dependenciesByVariant.keySet());

    List<String> currentGroup = Lists.newArrayList();
    while (!variants.isEmpty()) {
      String variant = variants.get(0);
      currentGroup.add(variant);

      if (variants.size() > 1) {
        List<PsdAndroidDependencyModel> variantDependencies = dependenciesByVariant.get(variant);
        for (int j = 1; j < variants.size(); j++) {
          String otherVariant = variants.get(j);
          List<PsdAndroidDependencyModel> otherVariantDependencies = dependenciesByVariant.get(otherVariant);
          if (variantDependencies.equals(otherVariantDependencies)) {
            currentGroup.add(otherVariant);
          }
        }
      }
      variantGroups.add(currentGroup);

      variants.removeAll(currentGroup);
      currentGroup = Lists.newArrayList();
    }

    Map<List<String>, List<PsdAndroidDependencyModel>> dependenciesByVariants = Maps.newHashMap();
    for (List<String> group : variantGroups) {
      String variant = group.get(0);
      dependenciesByVariants.put(group, dependenciesByVariant.get(variant));
    }
    return dependenciesByVariants;
  }

  private static class VariantComparator implements Comparator<PsdVariantModel> {
    @Override
    public int compare(PsdVariantModel v1, PsdVariantModel v2) {
      return v1.getName().compareTo(v2.getName());
    }
  }

  @VisibleForTesting
  static class ArtifactNameComparator implements Comparator<String> {
    @Override
    public int compare(String s1, String s2) {
      if (s1.equals(ARTIFACT_MAIN)) {
        return -1; // always first.
      }
      else if (s2.equals(ARTIFACT_MAIN)) {
        return 1;
      }
      return s1.compareTo(s2);
    }
  }
}
