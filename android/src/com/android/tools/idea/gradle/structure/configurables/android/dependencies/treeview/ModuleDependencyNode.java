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

import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsdNode;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.PsProject;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.android.tools.idea.gradle.structure.model.android.PsModuleDependency;
import com.google.common.collect.Lists;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.containers.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.builder.model.AndroidProject.ARTIFACT_MAIN;

public class ModuleDependencyNode extends AbstractDependencyNode<PsModuleDependency> {
  private final List<AbstractPsdNode<?>> myChildren = Lists.newArrayList();

  public ModuleDependencyNode(@NotNull AbstractPsdNode parent, @NotNull final PsModuleDependency dependency) {
    super(parent, dependency);
    setUp(dependency);
  }

  public ModuleDependencyNode(@NotNull AbstractPsdNode parent, @NotNull List<PsModuleDependency> dependencies) {
    super(parent, dependencies);
    setUp(dependencies.get(0));
  }

  private void setUp(@NotNull final PsModuleDependency moduleDependency) {
    myName = moduleDependency.getValueAsText();

    PsAndroidModule dependentModule = moduleDependency.getParent();
    PsProject project = dependentModule.getParent();

    PsModule referred = project.findModuleByGradlePath(moduleDependency.getGradlePath());
    if (referred instanceof PsAndroidModule) {
      PsAndroidModule androidModule = (PsAndroidModule)referred;
      androidModule.forEachDependency(new Predicate<PsAndroidDependency>() {
        @Override
        public boolean apply(@Nullable PsAndroidDependency dependency) {
          if (dependency == null || !dependency.isDeclared()) {
            return false; // Only show "declared" dependencies as top-level dependencies.
          }
          String moduleVariant = moduleDependency.getModuleVariant();
          if (!dependency.isIn(ARTIFACT_MAIN, moduleVariant)) {
            return false; // Only show the dependencies in the main artifact.
          }

          AbstractPsdNode<?> child = AbstractDependencyNode.createNode(ModuleDependencyNode.this, dependency);
          if (child != null) {
            myChildren.add(child);
            return true;
          }
          return false;
        }
      });
    }
  }

  @Override
  @NotNull
  public SimpleNode[] getChildren() {
    return myChildren.toArray(new SimpleNode[myChildren.size()]);
  }
}
