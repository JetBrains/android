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
import com.android.tools.idea.gradle.structure.configurables.android.treeview.AbstractVariantNode;
import com.android.tools.idea.gradle.structure.configurables.ui.PsdUISettings;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsdNode;
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidArtifactModel;
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidDependencyModel;
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidModuleModel;
import com.android.tools.idea.gradle.structure.model.android.PsdVariantModel;
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
  protected List<? extends AbstractVariantNode> createVariantNodes(@NotNull Collection<PsdVariantModel> variantModels) {
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
      // [Outer map] key: variant name, value: dependencies by artifact
      // [Inner map] key: artifact name, value: dependencies
      Map<String, Map<String, List<PsdAndroidDependencyModel>>> dependenciesByVariantAndArtifact = Maps.newHashMap();
      for (PsdAndroidDependencyModel dependency : dependencies) {
        if (!dependency.isEditable()) {
          continue; // Only show "declared" dependencies as top-level dependencies.
        }
        for (PsdAndroidDependencyModel.Container container : dependency.getContainers()) {
          Map<String, List<PsdAndroidDependencyModel>> dependenciesByArtifact =
            dependenciesByVariantAndArtifact.get(container.variant);

          if (dependenciesByArtifact == null) {
            dependenciesByArtifact = Maps.newHashMap();
            dependenciesByVariantAndArtifact.put(container.variant, dependenciesByArtifact);
          }

          List<PsdAndroidDependencyModel> dependencyModels = dependenciesByArtifact.get(container.artifact);
          if (dependencyModels == null) {
            dependencyModels = Lists.newArrayList();
            dependenciesByArtifact.put(container.artifact, dependencyModels);
          }

          dependencyModels.add(dependency);
        }
      }

      List<String> variantNames = Lists.newArrayList(dependenciesByVariantAndArtifact.keySet());
      Collections.sort(variantNames);

      for (String variantName : variantNames) {
        PsdVariantModel variantModel = variantsByName.get(variantName);
        VariantNode variantNode = new VariantNode(this, variantModel);

        List<AbstractPsdNode<?>> children = Lists.newArrayList();

        Map<String, List<PsdAndroidDependencyModel>> dependenciesByArtifact = dependenciesByVariantAndArtifact.get(variantName);

        if (dependenciesByArtifact != null) {
          List<PsdAndroidDependencyModel> mainArtifactDependencies = Collections.emptyList();

          String mainArtifactName = ARTIFACT_MAIN;
          if (dependenciesByArtifact.containsKey(mainArtifactName)) {
            mainArtifactDependencies = dependenciesByArtifact.get(mainArtifactName);
            children.addAll(createNodesFor(variantNode, mainArtifactDependencies));
            dependenciesByArtifact.remove(ARTIFACT_MAIN);
          }

          List<String> artifactNames = Lists.newArrayList(dependenciesByArtifact.keySet());
          Collections.sort(artifactNames, new ArtifactNameComparator());

          for (String artifactName : artifactNames) {
            PsdAndroidArtifactModel artifactModel = variantModel.findArtifact(artifactName);
            assert artifactModel != null;
            List<PsdAndroidDependencyModel> artifactDependencies = dependenciesByArtifact.get(artifactName);
            artifactDependencies.removeAll(mainArtifactDependencies); // Remove any dependencies already shown in "Main" artifact.

            if (!artifactDependencies.isEmpty()) {
              ArtifactNode artifactNode = new ArtifactNode(variantNode, artifactModel);
              artifactNode.setChildren(createNodesFor(artifactNode, artifactDependencies));
              children.add(artifactNode);
            }
          }
        }

        variantNode.setChildren(children);
        variantNodes.add(variantNode);
      }
    }

    return variantNodes;
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
