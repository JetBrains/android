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
import com.android.tools.idea.gradle.structure.configurables.android.treeview.AbstractVariantNode;
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidDependencyModel;
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidLibraryDependencyModel;
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidModuleModel;
import com.android.tools.idea.gradle.structure.model.android.PsdVariantModel;
import com.google.common.collect.Lists;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

class VariantNode extends AbstractVariantNode {
  private boolean myShowGroupId;
  private List<SimpleNode> myChildren;

  VariantNode(@NotNull PsdVariantModel model) {
    super(model);
  }

  @Override
  public SimpleNode[] getChildren() {
    if (myChildren == null) {
      List<SimpleNode> children = Lists.newArrayList();

      PsdVariantModel variantModel = getModel();

      PsdAndroidModuleModel moduleModel = variantModel.getParent();
      List<PsdAndroidDependencyModel> dependencies = moduleModel.getDependencies();
      Collections.sort(dependencies, new PsdAndroidDependencyModelComparator(myShowGroupId));

      for (PsdAndroidDependencyModel dependency : dependencies) {
        if (dependency instanceof PsdAndroidLibraryDependencyModel) {
          PsdAndroidLibraryDependencyModel libraryDependency = (PsdAndroidLibraryDependencyModel)dependency;
          if (libraryDependency.isInVariant(variantModel)) {
            children.add(new AndroidLibraryNode(libraryDependency, myShowGroupId));
          }
        }
      }

      myChildren = children;
    }

    return myChildren.toArray(new SimpleNode[myChildren.size()]);
  }
}
