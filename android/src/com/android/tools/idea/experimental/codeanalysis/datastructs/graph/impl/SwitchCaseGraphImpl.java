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
package com.android.tools.idea.experimental.codeanalysis.datastructs.graph.impl;

import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.SwitchCaseGraph;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.GraphNode;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.SwitchBranchingNode;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.impl.BlockGraphExitNodeImpl;
import com.google.common.collect.Sets;

import java.util.HashSet;

public class SwitchCaseGraphImpl extends BlockGraphImpl implements SwitchCaseGraph {

  protected HashSet<GraphNode> mCaseNodesSet;
  protected SwitchBranchingNode mSwitchBranchingNode;

  @Override
  public GraphNode[] getCaseNodes() {
    return mCaseNodesSet.toArray(GraphNode.EMPTY_ARRAY);
  }

  @Override
  public void addCase(GraphNode node) {
    mCaseNodesSet.add(node);
  }

  @Override
  public SwitchBranchingNode getSwitchBranchingNode() {
    return this.mSwitchBranchingNode;
  }

  public void setSwitchBranchingNode(SwitchBranchingNode node) {
    this.mSwitchBranchingNode = node;
  }


  public SwitchCaseGraphImpl() {
    super();
    mCaseNodesSet = Sets.newHashSet();


    if (mExitNode instanceof BlockGraphExitNodeImpl) {
      ((BlockGraphExitNodeImpl)mExitNode).setTag("[Switch]");
    }
  }
}
