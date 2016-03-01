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
import com.android.tools.idea.gradle.structure.configurables.android.treeview.AbstractRootNode;
import com.android.tools.idea.gradle.structure.configurables.ui.PsdUISettings;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsdNode;
import com.android.tools.idea.gradle.structure.model.android.*;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.util.containers.SortedList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    Map<String, PsdVariantModel> variantsByName = Maps.newHashMap();
    for (PsdVariantModel variantModel : variantModels) {
      variantsByName.put(variantModel.getName(), variantModel);
    }

    List<PsdAndroidDependencyModel> dependencies = getModels().get(0).getDependencies();

    if (myGroupVariants) {
      return createGroupedChildren(dependencies, variantsByName);
    }

    return createChildren(dependencies, variantsByName);
  }

  @NotNull
  private List<? extends AbstractPsdNode> createGroupedChildren(@NotNull List<PsdAndroidDependencyModel> dependencies,
                                                                @NotNull Map<String, PsdVariantModel> variantsByName) {
    Map<String, List<PsdDependencyContainer>> containersWithMainArtifactByVariant = Maps.newHashMap();

    Map<List<PsdDependencyContainer>, List<PsdAndroidDependencyModel>> groupedDependencies = group(dependencies);
    for (List<PsdDependencyContainer> containers : groupedDependencies.keySet()) {
      for (PsdDependencyContainer container : containers) {
        if (container.getArtifact().endsWith(ARTIFACT_MAIN)) {
          containersWithMainArtifactByVariant.put(container.getVariant(), containers);
          break;
        }
      }
    }

    List<ArtifactNode> children = Lists.newArrayList();

    for (List<PsdDependencyContainer> containers : groupedDependencies.keySet()) {
      List<PsdAndroidArtifactModel> groupArtifacts = extractArtifactModels(containers, variantsByName);

      ArtifactNode mainArtifactNode = null;
      if (!containersWithMainArtifactByVariant.values().contains(containers)) {
        // This is a node for "Unit Test" or "Android Test"
        if (containers.size() == 1) {
          // This is not a group. Create the "main" artifact node for the same variant
          PsdDependencyContainer container = containers.get(0);
          String variantName = container.getVariant();
          PsdVariantModel variant = variantsByName.get(variantName);
          assert variant != null;
          PsdAndroidArtifactModel mainArtifact = variant.findArtifact(ARTIFACT_MAIN);
          if (mainArtifact != null) {
            List<PsdDependencyContainer> mainArtifactContainers = containersWithMainArtifactByVariant.get(variantName);
            if (mainArtifactContainers != null) {
              List<PsdAndroidDependencyModel> mainArtifactDependencies = groupedDependencies.get(mainArtifactContainers);
              mainArtifactNode = createArtifactNode(mainArtifact, mainArtifactDependencies, null);
            }
          }
        }
        else {
          // Create the node that contains all the containers with "main" artifacts
          for (PsdDependencyContainer container : containers) {
            List<PsdDependencyContainer> mainArtifactContainers = containersWithMainArtifactByVariant.get(container.getVariant());
            if (mainArtifactContainers != null) {
              List<PsdAndroidArtifactModel> mainArtifacts = extractArtifactModels(mainArtifactContainers, variantsByName);
              mainArtifactNode = createArtifactNode(mainArtifacts, groupedDependencies.get(mainArtifactContainers), null);
              break;
            }
          }
        }
      }

      Collections.sort(groupArtifacts, ArtifactComparator.INSTANCE);
      ArtifactNode artifactNode = createArtifactNode(groupArtifacts, groupedDependencies.get(containers), mainArtifactNode);
      if (artifactNode != null) {
        children.add(artifactNode);
      }
    }

    Collections.sort(children, new Comparator<ArtifactNode>() {
      @Override
      public int compare(ArtifactNode a1, ArtifactNode a2) {
        return a1.getName().compareTo(a2.getName());
      }
    });
    return children;
  }

  @VisibleForTesting
  @NotNull
  static Map<List<PsdDependencyContainer>, List<PsdAndroidDependencyModel>> group(@NotNull List<PsdAndroidDependencyModel> dependencies) {
    Map<PsdDependencyContainer, List<PsdAndroidDependencyModel>> dependenciesByContainer = Maps.newHashMap();

    // Key: variant name
    Map<String, PsdDependencyContainer> containerWithMainArtifact = Maps.newHashMap();

    for (PsdAndroidDependencyModel dependency : dependencies) {
      Set<PsdDependencyContainer> containers = dependency.getContainers();
      for (PsdDependencyContainer container : containers) {
        if (container.getArtifact().equals(ARTIFACT_MAIN)) {
          containerWithMainArtifact.put(container.getVariant(), container);
        }
        List<PsdAndroidDependencyModel> containerDependencies = dependenciesByContainer.get(container);
        if (containerDependencies == null) {
          containerDependencies = new SortedList<PsdAndroidDependencyModel>(PsdAndroidDependencyModelComparator.INSTANCE);
          dependenciesByContainer.put(container, containerDependencies);
        }
        containerDependencies.add(dependency);
      }
    }

    List<List<PsdDependencyContainer>> containerGroups = Lists.newArrayList();
    List<PsdDependencyContainer> containers = Lists.newArrayList(dependenciesByContainer.keySet());

    List<PsdDependencyContainer> currentGroup = Lists.newArrayList();
    while (!containers.isEmpty()) {
      PsdDependencyContainer container1 = containers.get(0);
      currentGroup.add(container1);

      if (containers.size() > 1) {
        for (int i =  1; i < containers.size(); i++) {
          PsdDependencyContainer container2 = containers.get(i);
          if (haveSameDependencies(container1, container2, dependenciesByContainer)) {
            if (containerWithMainArtifact.values().contains(container1)) {
              // This is "main" artifact, no need to check any further
              currentGroup.add(container2);
            }
            else {
              // Check that the "main" artifacts in these variants are also similar.
              PsdDependencyContainer mainArtifactContainer1 = containerWithMainArtifact.get(container1.getVariant());
              PsdDependencyContainer mainArtifactContainer2 = containerWithMainArtifact.get(container2.getVariant());

              if (mainArtifactContainer1 == null && mainArtifactContainer2 == null) {
                currentGroup.add(container2);
              }
              if (mainArtifactContainer1 != null &&
                  mainArtifactContainer2 != null &&
                  haveSameDependencies(mainArtifactContainer1, mainArtifactContainer2, dependenciesByContainer)) {
                currentGroup.add(container2);
              }
            }
          }
        }
      }
      containerGroups.add(currentGroup);

      containers.removeAll(currentGroup);
      currentGroup = Lists.newArrayList();
    }

    Map<List<PsdDependencyContainer>, List<PsdAndroidDependencyModel>> dependenciesByContainers = Maps.newHashMap();
    for (List<PsdDependencyContainer> group : containerGroups) {
      PsdDependencyContainer container = group.get(0);
      dependenciesByContainers.put(group, dependenciesByContainer.get(container));
    }
    return dependenciesByContainers;
  }

  private static boolean haveSameDependencies(@NotNull PsdDependencyContainer c1,
                                              @NotNull PsdDependencyContainer c2,
                                              @NotNull Map<PsdDependencyContainer, List<PsdAndroidDependencyModel>> dependenciesByContainer) {
    if (c1.getArtifact().equals(c2.getArtifact())) {
      List<PsdAndroidDependencyModel> d1 = dependenciesByContainer.get(c1);
      List<PsdAndroidDependencyModel> d2 = dependenciesByContainer.get(c2);
      return d1.equals(d2);
    }
    return false;
  }

  @NotNull
  private static List<PsdAndroidArtifactModel> extractArtifactModels(@NotNull List<PsdDependencyContainer> containers,
                                                                     @NotNull Map<String, PsdVariantModel> variantsByName) {
    List<PsdAndroidArtifactModel> groupArtifacts = Lists.newArrayList();
    for (PsdDependencyContainer container : containers) {
      PsdAndroidArtifactModel foundArtifact = extractArtifactModel(container, variantsByName);
      groupArtifacts.add(foundArtifact);
    }
    return groupArtifacts;
  }

  @Nullable
  private static PsdAndroidArtifactModel extractArtifactModel(@NotNull PsdDependencyContainer container,
                                                              @NotNull Map<String, PsdVariantModel> variantsByName) {
    PsdVariantModel variant = variantsByName.get(container.getVariant());
    assert variant != null;
    PsdAndroidArtifactModel artifact = variant.findArtifact(container.getArtifact());
    assert artifact != null;
    return artifact;
  }

  @Nullable
  private ArtifactNode createArtifactNode(@NotNull List<PsdAndroidArtifactModel> artifactModels,
                                          @NotNull List<PsdAndroidDependencyModel> artifactDependencies,
                                          @Nullable ArtifactNode mainArtifactNode) {
    if (!artifactDependencies.isEmpty() || mainArtifactNode != null) {
      ArtifactNode artifactNode = new ArtifactNode(this, artifactModels);
      populate(artifactNode, artifactDependencies, mainArtifactNode);
      return artifactNode;
    }
    return null;
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

      Map<String, List<PsdAndroidDependencyModel>> dependenciesByArtifact = dependenciesByVariantAndArtifact.get(variantName);

      if (dependenciesByArtifact != null) {
        List<String> artifactNames = Lists.newArrayList(dependenciesByArtifact.keySet());
        //noinspection TestOnlyProblems
        Collections.sort(artifactNames, ArtifactNameComparator.INSTANCE);

        for (String artifactName : artifactNames) {
          PsdAndroidArtifactModel artifactModel = variantModel.findArtifact(artifactName);
          assert artifactModel != null;

          ArtifactNode mainArtifactNode = null;
          String mainArtifactName = ARTIFACT_MAIN;
          if (!mainArtifactName.equals(artifactName)) {
            // Add "main" artifact as a dependency of "unit test" or "android test" artifact.
            PsdAndroidArtifactModel mainArtifactModel = variantModel.findArtifact(mainArtifactName);
            if (mainArtifactModel != null) {
              List<PsdAndroidDependencyModel> artifactDependencies = dependenciesByArtifact.get(mainArtifactName);
              if (artifactDependencies == null) {
                artifactDependencies = Collections.emptyList();
              }
              mainArtifactNode = createArtifactNode(mainArtifactModel, artifactDependencies, null);
            }
          }

          ArtifactNode artifactNode = createArtifactNode(artifactModel, dependenciesByArtifact.get(artifactName), mainArtifactNode);
          if (artifactNode != null) {
            childrenNodes.add(artifactNode);
          }
        }
      }
    }

    return childrenNodes;
  }

  @Nullable
  private ArtifactNode createArtifactNode(@NotNull PsdAndroidArtifactModel artifactModel,
                                          @NotNull List<PsdAndroidDependencyModel> artifactDependencies,
                                          @Nullable ArtifactNode mainArtifactNode) {
    if (!artifactDependencies.isEmpty() || mainArtifactNode != null) {
      ArtifactNode artifactNode = new ArtifactNode(this, artifactModel);
      populate(artifactNode, artifactDependencies, mainArtifactNode);
      return artifactNode;
    }
    return null;
  }

  private static void populate(@NotNull ArtifactNode artifactNode,
                               @NotNull List<PsdAndroidDependencyModel> dependencies,
                               @Nullable ArtifactNode mainArtifactNode) {
    List<AbstractPsdNode<?>> children = createNodesFor(artifactNode, dependencies);
    if (mainArtifactNode != null) {
      children.add(0, mainArtifactNode);
    }
    artifactNode.setChildren(children);
  }

  private static class ArtifactComparator implements Comparator<PsdAndroidArtifactModel> {
    static final ArtifactComparator INSTANCE = new ArtifactComparator();

    @Override
    public int compare(PsdAndroidArtifactModel a1, PsdAndroidArtifactModel a2) {
      PsdVariantModel v1 = a1.getParent();
      PsdVariantModel v2 = a2.getParent();
      int compareVariantName = v1.getName().compareTo(v2.getName());
      if (compareVariantName == 0) {
        //noinspection TestOnlyProblems
        return ArtifactNameComparator.INSTANCE.compare(a1.getName(), a2.getName());
      }
      return compareVariantName;
    }
  }

  @VisibleForTesting
  static class ArtifactNameComparator implements Comparator<String> {
    static final ArtifactNameComparator INSTANCE = new ArtifactNameComparator();

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
