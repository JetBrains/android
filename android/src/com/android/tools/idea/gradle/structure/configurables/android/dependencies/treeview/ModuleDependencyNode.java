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

import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ModuleDependencyModel;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsModelNode;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsNode;
import com.android.tools.idea.gradle.structure.model.PsDeclaredDependency;
import com.android.tools.idea.gradle.structure.model.PsModel;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidArtifact;
import com.android.tools.idea.gradle.structure.model.android.PsModuleAndroidDependency;
import com.android.tools.idea.gradle.structure.model.android.PsResolvedModuleAndroidDependency;
import com.google.common.collect.Lists;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.DependencyNodes.createNodesForResolvedDependencies;
import static com.android.tools.idea.gradle.structure.model.PsDependency.TextType.PLAIN_TEXT;

public class ModuleDependencyNode extends AbstractDependencyNode<PsModuleAndroidDependency> {
  private final List<AbstractPsModelNode<?>> myChildren = Lists.newArrayList();

  public ModuleDependencyNode(@NotNull AbstractPsNode parent,
                              @NotNull PsModuleAndroidDependency dependency) {
    super(parent, dependency);
    setUp(dependency);
  }

  public ModuleDependencyNode(@NotNull AbstractPsNode parent,
                              @NotNull List<PsModuleAndroidDependency> dependencies) {
    super(parent, dependencies);
    setUp(dependencies.get(0));
  }

  private void setUp(@NotNull PsModuleAndroidDependency moduleDependency) {
    myName = moduleDependency.toText(PLAIN_TEXT);
    if (moduleDependency instanceof PsResolvedModuleAndroidDependency) {
      PsAndroidArtifact referredModuleMainArtifact = ((PsResolvedModuleAndroidDependency)moduleDependency).findReferredArtifact();
      if (referredModuleMainArtifact != null) {
        List<AbstractPsModelNode<?>> children = createNodesForResolvedDependencies(this, referredModuleMainArtifact);
        myChildren.addAll(children);
      }
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

      List<PsModuleAndroidDependency> models = getModels();
      for (PsModuleAndroidDependency ourModel : models) {
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
