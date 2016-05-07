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
package com.android.tools.idea.gradle.structure.configurables.ui.treeview;

import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class PsRootNode extends AbstractPsNode {
  @NotNull private List<? extends AbstractPsNode> myChildren = Collections.emptyList();

  public PsRootNode() {
    setAutoExpandNode(true);
  }

  public void setChildren(@NotNull List<? extends AbstractPsNode> children) {
    myChildren = children;
  }

  @Override
  public SimpleNode[] getChildren() {
    return myChildren.isEmpty() ? NO_CHILDREN : myChildren.toArray(new SimpleNode[myChildren.size()]);
  }
}
