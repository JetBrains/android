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

import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.AbstractDependencyNode;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.AndroidArtifactNode;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractBaseTreeStructure;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.SimpleNodeComparator;
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsProject;
import com.android.tools.idea.gradle.structure.model.android.*;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.android.builder.model.AndroidProject.ARTIFACT_MAIN;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

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

  void displayTargetModules(@NotNull List<AbstractDependencyNode<? extends PsAndroidDependency>> dependencyNodes) {
    // Key: module name, Value: pair of module and version of the dependency used in the module.
    Map<String, Pair<PsAndroidModule, String>> modules = Maps.newHashMap();

    // Key: module name, Value: dependency containers (variant/artifact combo) per module.
    Map<String, Set<PsDependencyContainer>> containersByModule = Maps.newHashMap();

    // From the list of AbstractDependencyNode:
    //  1. Extract modules (to show them as top-level nodes)
    //  2. Extract variants/artifact (i.e. PsDependencyContainer) per module (to show them as children nodes of the module nodes)
    for (AbstractDependencyNode<? extends PsAndroidDependency> node : dependencyNodes) {
      AbstractDependencyNode<? extends PsAndroidDependency> declared = getTopParent(node);

      for (PsAndroidDependency dependency : node.getModels()) {
        String version = "";
        PsAndroidModule module = dependency.getParent();

        String configurationName = dependency.getConfigurationName();
        if (isEmpty(configurationName) || node != declared) {
          // This is a transitive dependency. Need to find the dependency that brought it. Then we used this newly found dependency
          // to get the module that declared it and the configuration name used.
          PsAndroidDependency declaredDependency = getDeclaredDependency(declared);
          configurationName = declaredDependency.getConfigurationName();
          module = declaredDependency.getParent();
        }
        assert isNotEmpty(configurationName);

        String moduleName = module.getName();

        if (dependency instanceof PsLibraryDependency) {
          PsLibraryDependency libraryDependency = (PsLibraryDependency)dependency;
          PsArtifactDependencySpec spec = libraryDependency.getDeclaredSpec();
          if (spec == null) {
            spec = libraryDependency.getResolvedSpec();
          }
          // For library dependencies we display the version of the library being used.
          version = spec.version;
        }
        Pair<PsAndroidModule, String> existing = modules.get(moduleName);
        if (existing == null || isEmpty(existing.getSecond())) {
          modules.put(moduleName, Pair.create(module, version));
        }

        Set<PsDependencyContainer> containers = containersByModule.get(moduleName);
        if (containers == null) {
          containers = Sets.newHashSet();
          containersByModule.put(moduleName, containers);
        }

        for (PsDependencyContainer container : dependency.getContainers()) {
          PsAndroidArtifact artifact = container.findArtifact(module, false);
          if (artifact != null && artifact.getPossibleConfigurationNames().contains(configurationName)) {
            containers.add(container);
          }
        }
      }
    }

    // Now we create the tree nodes.
    List<TargetAndroidModuleNode> children = Lists.newArrayList();

    for (Pair<PsAndroidModule, String> moduleAndVersion : modules.values()) {
      PsAndroidModule module = moduleAndVersion.getFirst();
      TargetAndroidModuleNode moduleNode = new TargetAndroidModuleNode(myRootNode, module, moduleAndVersion.getSecond());

      List<AndroidArtifactNode> artifactNodes = Lists.newArrayList();

      Set<PsDependencyContainer> containers = containersByModule.get(module.getName());
      if (containers != null) {
        // Key: variant name, Value: artifacts.
        Map<String, Set<PsAndroidArtifact>> containersByVariant = Maps.newHashMap();

        for (PsDependencyContainer container : containers) {
          PsAndroidArtifact artifact = container.findArtifact(module, true);

          String variant = container.getVariant();
          Set<PsAndroidArtifact> existingArtifacts = containersByVariant.get(variant);
          if (existingArtifacts == null) {
            existingArtifacts = Sets.newHashSet();
            containersByVariant.put(variant, existingArtifacts);
          }
          existingArtifacts.add(artifact);

          AndroidArtifactNode artifactNode = new AndroidArtifactNode(moduleNode, artifact);
          artifactNodes.add(artifactNode);
        }

        for (String variant : containersByVariant.keySet()) {
          Set<PsAndroidArtifact> existingArtifacts = containersByVariant.get(variant);
          PsAndroidArtifact mainArtifact = findMainArtifact(existingArtifacts);
          if (mainArtifact != null) {
            // The dependency is declared in the main artifact. Both test artifacts should be added as well, since they inherit the
            // dependencies.
            PsVariant parentVariant = mainArtifact.getParent();
            parentVariant.forEachArtifact(artifact -> {
              if (artifact == null || existingArtifacts.contains(artifact)) {
                return false;
              }
              AndroidArtifactNode artifactNode = new AndroidArtifactNode(moduleNode, artifact);
              artifactNodes.add(artifactNode);
              return true;
            });
          }
        }

        Collections.sort(artifactNodes, new SimpleNodeComparator<>());
        moduleNode.setChildren(artifactNodes);
      }
      children.add(moduleNode);
    }

    Collections.sort(children, new SimpleNodeComparator<>());
    myRootNode.setChildren(children);
  }

  @NotNull
  private static AbstractDependencyNode<? extends PsAndroidDependency> getTopParent(AbstractDependencyNode<?> node) {
    SimpleNode current = node;
    while (true) {
      SimpleNode parent = current.getParent();
      if (parent instanceof AbstractDependencyNode) {
        current = parent;
        continue;
      }
      return (AbstractDependencyNode<? extends PsAndroidDependency>)current;
    }
  }

  @NotNull
  private static PsAndroidDependency getDeclaredDependency(AbstractDependencyNode<? extends PsAndroidDependency> node) {
    PsAndroidDependency found = null;
    for (PsAndroidDependency dependency : node.getModels()) {
      String configurationName = dependency.getConfigurationName();
      if (isNotEmpty(configurationName)) {
        found = dependency;
        break;
      }
    }
    assert found != null;
    return found;
  }

  @Nullable
  private static PsAndroidArtifact findMainArtifact(@NotNull Collection<PsAndroidArtifact> artifacts) {
    for (PsAndroidArtifact artifact : artifacts) {
      if (artifact.getResolvedName().equals(ARTIFACT_MAIN)) {
        return artifact;
      }
    }
    return null;
  }
}
