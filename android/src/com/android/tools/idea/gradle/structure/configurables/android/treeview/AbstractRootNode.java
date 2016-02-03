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
package com.android.tools.idea.gradle.structure.configurables.android.treeview;

import com.android.tools.idea.gradle.structure.model.android.PsdAndroidModuleModel;
import com.android.tools.idea.gradle.structure.model.android.PsdVariantModel;
import com.google.common.collect.Lists;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class AbstractRootNode extends AbstractPsdNode {
  @NotNull private final PsdAndroidModuleModel myModel;

  public AbstractRootNode(@NotNull PsdAndroidModuleModel model) {
    myModel = model;
    setAutoExpandNode(true);
  }

  @Override
  public SimpleNode[] getChildren() {
    List<SimpleNode> variantNodes = Lists.newArrayList();
    for (PsdVariantModel variantModel : myModel.getVariantModels()) {
      AbstractVariantNode variantNode = createVariantNode(variantModel);
      variantNodes.add(variantNode);
    }
    return variantNodes.toArray(new SimpleNode[variantNodes.size()]);
  }

  @NotNull
  protected abstract AbstractVariantNode createVariantNode(@NotNull PsdVariantModel variantModel);

  @NotNull
  public PsdAndroidModuleModel getModel() {
    return myModel;
  }
}
