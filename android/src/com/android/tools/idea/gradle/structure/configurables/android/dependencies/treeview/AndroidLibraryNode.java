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
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidDependencyModel;
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidLibraryDependencyModel;
import com.google.common.collect.Lists;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.tools.idea.gradle.structure.configurables.android.dependencies.ArtifactDependencySpecs.asText;

class AndroidLibraryNode extends AbstractDependencyNode<PsdAndroidLibraryDependencyModel> {
  private final boolean myShowGroupId;

  private List<SimpleNode> myChildren;

  AndroidLibraryNode(@NotNull PsdAndroidLibraryDependencyModel model, boolean showGroupId) {
    super(model);
    myShowGroupId = showGroupId;
    ArtifactDependencySpec spec = model.getSpec();
    myName = asText(spec, showGroupId);
    setIcon(model.getIcon());
    setAutoExpandNode(true);
  }

  @Override
  public SimpleNode[] getChildren() {
    if (myChildren == null) {
      List<SimpleNode> children = Lists.newArrayList();
      for (PsdAndroidDependencyModel transitive : getModels().get(0).getTransitiveDependencies()) {
        if (transitive instanceof PsdAndroidLibraryDependencyModel) {
          PsdAndroidLibraryDependencyModel transitiveLibrary = (PsdAndroidLibraryDependencyModel)transitive;
          AndroidLibraryNode child = new AndroidLibraryNode(transitiveLibrary, myShowGroupId);
          children.add(child);
        }
      }

      myChildren = children;
    }
    return myChildren.toArray(new SimpleNode[myChildren.size()]);
  }
}
