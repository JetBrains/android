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
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.PsRootNode;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractBaseTreeStructure;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.SimpleNodeComparator;
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.android.*;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class TargetModulesTreeStructure extends AbstractBaseTreeStructure {
  @NotNull private final PsRootNode myRootNode = new PsRootNode();

  @Override
  @NotNull
  public Object getRootElement() {
    return myRootNode;
  }

  void displayTargetModules(@NotNull List<AbstractDependencyNode<? extends PsAndroidDependency>> dependencyNodes) {
    // Key: module name, Value: pair of module and version of the dependency used in the module.
    Map<String, Pair<PsAndroidModule, String>> modules = Maps.newHashMap();

    // Key: module name, Value: configuration names.
    Multimap<String, Configuration> configurationNamesByModule = HashMultimap.create();

    // From the list of AbstractDependencyNode:
    //  1. Extract modules (to show them as top-level nodes)
    //  2. Extract variants/artifact (i.e. PsDependencyContainer) per module (to show them as children nodes of the module nodes)
    dependencyNodes.forEach(node -> {
      List<AbstractDependencyNode<? extends PsAndroidDependency>> declaredDependencyNodes = getDeclaredDependencyNodeHierarchy(node);

      // Create the module and version used.
      Map<String, String> versionByModule = Maps.newHashMap();
      for (PsAndroidDependency dependency : node.getModels()) {
        if (dependency instanceof PsLibraryAndroidDependency) {
          PsLibraryAndroidDependency libraryDependency = (PsLibraryAndroidDependency)dependency;
          PsArtifactDependencySpec spec = libraryDependency.getDeclaredSpec();
          if (spec == null) {
            spec = libraryDependency.getResolvedSpec();
          }
          // For library dependencies we display the version of the library being used.
          PsAndroidModule module = dependency.getParent();
          versionByModule.put(module.getName(), spec.version);
        }
      }

      AbstractDependencyNode<? extends PsAndroidDependency> topParentNode = declaredDependencyNodes.get(declaredDependencyNodes.size() - 1);
      for (PsAndroidDependency dependency : topParentNode.getModels()) {
        PsAndroidModule module = dependency.getParent();
        String moduleName = module.getName();
        Pair<PsAndroidModule, String> existing = modules.get(moduleName);
        if (existing == null) {
          modules.put(moduleName, Pair.create(module, versionByModule.get(moduleName)));
        }
      }

      declaredDependencyNodes.forEach(declaredDependencyNode -> {
        List<PsAndroidDependency> declaredDependencies = getDeclaredDependencies(declaredDependencyNode);

        declaredDependencies.forEach(declaredDependency -> {
          List<String> configurationNames = declaredDependency.getConfigurationNames();
          assert !configurationNames.isEmpty();
          PsAndroidModule module = declaredDependency.getParent();
          String moduleName = module.getName();

          for (PsDependencyContainer container : declaredDependency.getContainers()) {
            PsAndroidArtifact artifact = container.findArtifact(module, false);

            for (String configurationName : configurationNames) {
              if (artifact != null && artifact.containsConfigurationName(configurationName)) {
                boolean transitive = declaredDependencyNode != node;

                Collection<Configuration> configurations = configurationNamesByModule.get(moduleName);
                boolean found = false;
                for (Configuration configuration : configurations) {
                  if (configuration.getName().equals(configurationName)) {
                    configuration.addType(transitive);
                    found = true;
                    break;
                  }
                }

                if (!found) {
                  Icon icon = artifact.getIcon();
                  configurationNamesByModule.put(moduleName, new Configuration(configurationName, icon, transitive));
                }
              }
            }
          }
        });
      });
    });

    // Now we create the tree nodes.
    List<TargetAndroidModuleNode> children = Lists.newArrayList();

    for (Pair<PsAndroidModule, String> moduleAndVersion : modules.values()) {
      PsAndroidModule module = moduleAndVersion.getFirst();
      TargetAndroidModuleNode moduleNode = new TargetAndroidModuleNode(myRootNode, module, moduleAndVersion.getSecond());

      List<Configuration> configurations = Lists.newArrayList(configurationNamesByModule.get(module.getName()));
      Collections.sort(configurations);

      List<TargetConfigurationNode> nodes = Lists.newArrayList();
      configurations.forEach(configuration -> nodes.add(new TargetConfigurationNode(configuration)));

      moduleNode.setChildren(nodes);
      children.add(moduleNode);
    }

    Collections.sort(children, new SimpleNodeComparator<>());
    myRootNode.setChildren(children);
  }

  @NotNull
  private static List<AbstractDependencyNode<? extends PsAndroidDependency>> getDeclaredDependencyNodeHierarchy(AbstractDependencyNode<?> node) {
    List<AbstractDependencyNode<? extends PsAndroidDependency>> nodes = Lists.newArrayList();
    if (node.isDeclared()) {
      nodes.add(node);
    }
    SimpleNode current = node;
    while (true) {
      SimpleNode parent = current.getParent();
      if (parent instanceof AbstractDependencyNode && ((AbstractDependencyNode)parent).isDeclared()) {
        nodes.add((AbstractDependencyNode<? extends PsAndroidDependency>)parent);
      }
      else if (parent == null) {
        break;
      }
      current = parent;
    }
    return nodes;
  }

  @NotNull
  private static List<PsAndroidDependency> getDeclaredDependencies(AbstractDependencyNode<? extends PsAndroidDependency> node) {
    return node.getModels().stream().filter(PsAndroidDependency::isDeclared).collect(Collectors.toList());
  }
}
