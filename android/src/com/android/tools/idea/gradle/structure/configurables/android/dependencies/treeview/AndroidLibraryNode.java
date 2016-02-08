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

import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.configurables.ui.PsdUISettings;
import com.android.tools.idea.gradle.structure.model.PsdModel;
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidDependencyModel;
import com.android.tools.idea.gradle.structure.model.android.PsdLibraryDependencyModel;
import com.google.common.collect.Lists;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.tools.idea.gradle.structure.configurables.android.dependencies.ArtifactDependencySpecs.asText;

class AndroidLibraryNode extends AbstractDependencyNode<PsdLibraryDependencyModel> {
  private List<SimpleNode> myChildren;

  AndroidLibraryNode(@NotNull PsdLibraryDependencyModel model) {
    super(model);
    boolean showGroupId = PsdUISettings.getInstance().DECLARED_DEPENDENCIES_SHOW_GROUP_ID;
    ArtifactDependencySpec spec = model.getResolvedSpec();
    myName = asText(spec, showGroupId);
    setIcon(model.getIcon());
    setAutoExpandNode(true);
  }

  @Override
  public SimpleNode[] getChildren() {
    if (myChildren == null) {
      List<SimpleNode> children = Lists.newArrayList();
      for (PsdAndroidDependencyModel transitive : getModels().get(0).getTransitiveDependencies()) {
        if (transitive instanceof PsdLibraryDependencyModel) {
          PsdLibraryDependencyModel transitiveLibrary = (PsdLibraryDependencyModel)transitive;
          AndroidLibraryNode child = new AndroidLibraryNode(transitiveLibrary);
          children.add(child);
        }
      }

      myChildren = children;
    }
    return myChildren.toArray(new SimpleNode[myChildren.size()]);
  }

  @Override
  public boolean matches(@NotNull PsdModel model) {
    if (model instanceof PsdLibraryDependencyModel) {
      PsdLibraryDependencyModel other = (PsdLibraryDependencyModel)model;

      List<PsdLibraryDependencyModel> models = getModels();
      int modelCount = models.size();
      if (modelCount == 1) {
        PsdLibraryDependencyModel myModel = models.get(0);
        return myModel.getResolvedSpec().equals(other.getResolvedSpec());
      }
    }
    return false;
  }
}
