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

import com.android.tools.idea.gradle.structure.configurables.android.treeview.AbstractVariantNode;
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidDependencyModel;
import com.android.tools.idea.gradle.structure.model.android.PsdLibraryDependencyModel;
import com.android.tools.idea.gradle.structure.model.android.PsdVariantModel;
import com.google.common.collect.Lists;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

class VariantNode extends AbstractVariantNode {
  private List<SimpleNode> myChildren = Collections.emptyList();

  public VariantNode(@NotNull PsdVariantModel model) {
    super(model);
  }

  public VariantNode(@NotNull List<PsdVariantModel> models) {
    super(models);
  }

  @Override
  public SimpleNode[] getChildren() {
    return myChildren.toArray(new SimpleNode[myChildren.size()]);
  }

  void setChildren(@NotNull Collection<PsdAndroidDependencyModel> dependencies) {
    myChildren = Lists.newArrayList();

    for (PsdAndroidDependencyModel dependency : dependencies) {
      if (dependency instanceof PsdLibraryDependencyModel) {
        myChildren.add(new AndroidLibraryNode((PsdLibraryDependencyModel)dependency));
      }
    }
  }
}
