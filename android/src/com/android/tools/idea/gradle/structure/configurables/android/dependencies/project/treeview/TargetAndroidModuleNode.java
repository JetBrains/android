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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.project.treeview;

import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.AndroidArtifactNode;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsdNode;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class TargetAndroidModuleNode extends AbstractPsdNode<PsAndroidModule> {
  private List<AndroidArtifactNode> myChildren = Collections.emptyList();

  TargetAndroidModuleNode(@NotNull AbstractPsdNode<?> parent, @NotNull PsAndroidModule module) {
    super(parent, module);
    setAutoExpandNode(true);
  }

  @Override
  public SimpleNode[] getChildren() {
    return myChildren.toArray(new SimpleNode[myChildren.size()]);
  }

  void setChildren(@NotNull List<AndroidArtifactNode> children) {
    myChildren = children;
  }
}
