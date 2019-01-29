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
package com.android.tools.idea.gradle.structure.configurables.dependencies.treeview;

import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ModuleDependencyModel;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsModelNode;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsNode;
import com.android.tools.idea.gradle.structure.model.*;
import com.google.common.collect.Lists;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static com.android.tools.idea.gradle.structure.configurables.dependencies.treeview.DependencyNodesKt.createNodesForResolvedDependencies;

public class ModuleDependencyNode extends AbstractDependencyNode<PsModuleDependency> {
  private final List<AbstractPsModelNode<?>> myChildren = Lists.newArrayList();

  public ModuleDependencyNode(@NotNull AbstractPsNode parent,
                              @NotNull PsResolvedModuleDependency dependency) {
    super(parent, dependency);
    myName = dependency.toText();
    setUp(dependency);
  }

  public ModuleDependencyNode(@NotNull AbstractPsNode parent,
                              @NotNull Collection<PsDeclaredModuleDependency> dependencies) {
    super(parent, dependencies.stream().map(it -> (PsModuleDependency)it).collect(Collectors.toList()));
    myName = getFirstModel().toText();
  }

  private void setUp(@NotNull PsResolvedModuleDependency moduleDependency) {
    @Nullable PsDependencyCollection<?, ?, ?> dependencies = PsModuleDependencyKt.getTargetModuleResolvedDependencies(moduleDependency);
    if (dependencies != null) {
      List<AbstractPsModelNode<?>> children =
        createNodesForResolvedDependencies(this, dependencies);
      myChildren.addAll(children);
    }
  }

  @Override
  @NotNull
  public SimpleNode[] getChildren() {
    return myChildren.toArray(new SimpleNode[0]);
  }

  @Override
  public boolean matches(@NotNull PsModel model) {
    // Only top level LibraryDependencyNodes can match declared dependencies.
    if (model instanceof PsDeclaredDependency) {
      PsDeclaredDependency other = (PsDeclaredDependency)model;

      List<PsModuleDependency> models = getModels();
      for (PsModuleDependency ourModel : models) {
        List<DependencyModel> ourParsedModels = Companion.getDependencyParsedModels(ourModel);
        if (ourParsedModels == null) continue;
        for (DependencyModel resolvedFromParsedDependency : ourParsedModels) {
          // other.getParsedModels() always contains just one model since it is a declared dependency.
          if (other.getParsedModel()
                instanceof ModuleDependencyModel && resolvedFromParsedDependency instanceof ModuleDependencyModel) {
            ModuleDependencyModel theirs = (ModuleDependencyModel)other.getParsedModel();
            ModuleDependencyModel ours = (ModuleDependencyModel)resolvedFromParsedDependency;
            return
              theirs.configurationName().equals(ours.configurationName())
              && theirs.name().equals(ours.name());
          }
        }
      }
    }
    return false;
  }
}
