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

import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsModelNode;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidArtifact;
import com.android.tools.idea.gradle.structure.model.android.PsVariant;
import com.google.common.collect.Lists;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AndroidArtifactNode extends AbstractPsModelNode<PsAndroidArtifact> {
  @NotNull private List<AbstractPsModelNode<?>> myChildren = Lists.newArrayList();

  public AndroidArtifactNode(@NotNull AbstractPsModelNode<?> parent, @NotNull PsAndroidArtifact artifact) {
    super(parent, artifact);
    setAutoExpandNode(true);
  }

  public AndroidArtifactNode(@NotNull AbstractPsModelNode<?> parent, @NotNull List<PsAndroidArtifact> artifacts) {
    super(parent, artifacts);
    setAutoExpandNode(true);
  }

  @Override
  @NotNull
  protected String nameOf(PsAndroidArtifact artifact) {
    PsVariant variant = artifact.getParent();
    return variant.getName() + artifact.getName();
  }

  @Override
  public SimpleNode[] getChildren() {
    return myChildren.toArray(new SimpleNode[myChildren.size()]);
  }

  public void setChildren(@NotNull List<AbstractPsModelNode<?>> children) {
    myChildren = children;
  }
}
