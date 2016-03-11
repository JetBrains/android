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
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsdNode;
import com.android.tools.idea.gradle.structure.model.PsdModuleModel;
import com.android.tools.idea.gradle.structure.model.PsdProjectModel;
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidDependencyModel;
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidModuleModel;
import com.android.tools.idea.gradle.structure.model.android.PsdLibraryDependencyModel;
import com.android.tools.idea.gradle.structure.model.android.PsdModuleDependencyModel;
import com.google.common.collect.Lists;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

import static com.android.builder.model.AndroidProject.ARTIFACT_MAIN;

public class ModuleDependencyNode extends AbstractDependencyNode<PsdModuleDependencyModel> {
  private final List<AbstractPsdNode<?>> myChildren = Lists.newArrayList();

  ModuleDependencyNode(@NotNull AbstractPsdNode parent, @NotNull PsdModuleDependencyModel model) {
    super(parent, model);
    myName = model.getValueAsText();

    PsdAndroidModuleModel dependentModule = model.getParent();
    PsdProjectModel project = dependentModule.getParent();

    PsdModuleModel referred = project.findModelByGradlePath(model.getGradlePath());
    if (referred instanceof PsdAndroidModuleModel) {
      List<PsdAndroidDependencyModel> dependencies = ((PsdAndroidModuleModel)referred).getDependencies();
      Collections.sort(dependencies, PsdAndroidDependencyModelComparator.INSTANCE);

      for (PsdAndroidDependencyModel dependency : dependencies) {
        if (!dependency.isEditable()) {
          continue; // Only show "declared" dependencies as top-level dependencies.
        }
        String moduleVariant = model.getModuleVariant();
        if (!dependency.isIn(ARTIFACT_MAIN, moduleVariant)) {
          continue; // Only show the dependencies in the main artifact.
        }

        AbstractPsdNode<?> child = null;

        if (dependency instanceof PsdLibraryDependencyModel) {
          child = new LibraryDependencyNode(this, (PsdLibraryDependencyModel)dependency);
        }
        else if (dependency instanceof PsdModuleDependencyModel) {
          child = new ModuleDependencyNode(this, (PsdModuleDependencyModel)dependency);
        }

        if (child != null) {
          myChildren.add(child);
        }
      }
    }
  }

  @Override
  public SimpleNode[] getChildren() {
    return myChildren.toArray(new SimpleNode[myChildren.size()]);
  }
}
