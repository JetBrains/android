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
import com.android.tools.idea.gradle.structure.configurables.android.treeview.AbstractPsdNode;
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidLibraryDependencyModel;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.structure.configurables.android.dependencies.ArtifactDependencySpecs.asText;

class AndroidLibraryNode extends AbstractPsdNode<PsdAndroidLibraryDependencyModel> {
  AndroidLibraryNode(@NotNull PsdAndroidLibraryDependencyModel model, boolean showGroupId) {
    super(model);
    ArtifactDependencySpec spec = model.getSpec();
    myName = asText(spec, showGroupId);
    setIcon(model.getIcon());
  }

  @Override
  public SimpleNode[] getChildren() {
    return new SimpleNode[0];
  }
}
