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
package com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.impl;

import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.BlockGraph;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.GraphNode;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.GraphNodeUtil;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.IfBranchingNode;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.Value;

public class IfBranchingNodeImpl extends ConditionCheckNodeImpl implements IfBranchingNode {
  protected BlockGraph mThenBranchCFG;
  protected BlockGraph mElseBranchCFG;
  protected GraphNode mNonBranchingTarget;

  public IfBranchingNodeImpl(BlockGraph parentGraph, Value checkedVal, BlockGraph thenBranch, BlockGraph elseBranch) {
    super(parentGraph, checkedVal);
    mThenBranchCFG = thenBranch;
    mElseBranchCFG = elseBranch;
    if (thenBranch != null)
    //this.mTrueNode.addOut(thenBranch.getEntryNode());
    {
      GraphNodeUtil.connectGraphNode(this.mTrueNode, thenBranch.getEntryNode());
    }
    if (elseBranch != null)
    //this.mFalseNode.addOut(elseBranch.getEntryNode());
    {
      GraphNodeUtil.connectGraphNode(this.mFalseNode, elseBranch.getEntryNode());
    }
  }

  @Override
  public BlockGraph getThenBranchCFG() {
    return this.mThenBranchCFG;
  }

  @Override
  public BlockGraph getElseBranchCFG() {
    return this.mElseBranchCFG;
  }

  @Override
  public boolean hasElse() {
    return mElseBranchCFG != null;
  }

  @Override
  public String getSimpleName() {
    return String.format("If Check on %s ", this.mCheckedValue.getSimpleName());
  }
}
