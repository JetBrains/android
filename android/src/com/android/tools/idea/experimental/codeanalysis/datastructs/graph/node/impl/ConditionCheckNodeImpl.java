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
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.ConditionCheckNode;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.GraphNode;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.GraphNodeUtil;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.Value;

public class ConditionCheckNodeImpl extends GraphNodeImpl implements ConditionCheckNode {

  protected ConditionTrueNode mTrueNode;
  protected ConditionFalseNode mFalseNode;
  protected Value mCheckedValue;

  @Override
  public Value getCheckedValue() {
    return mCheckedValue;
  }

  @Override
  public GraphNode getTrueBranch() {
    return mTrueNode;
  }

  @Override
  public GraphNode getFalseBranch() {
    return mFalseNode;
  }

  @Override
  public GraphNode[] getOut() {
    GraphNode[] retArray = new GraphNode[2];
    retArray[0] = mTrueNode;
    retArray[1] = mFalseNode;

    return retArray;
  }

  @Override
  public void addOut(GraphNode out) {

  }

  @Override
  public void removeOut(GraphNode out) {

  }

  @Override
  public String getSimpleName() {
    return String.format("ConditionCheck on %s", mCheckedValue.getSimpleName());
  }

  public ConditionCheckNodeImpl(BlockGraph parentGraph, Value checkedVal) {
    super(parentGraph);
    this.mCheckedValue = checkedVal;
    this.mTrueNode = new ConditionTrueNode(parentGraph);
    this.mFalseNode = new ConditionFalseNode(parentGraph);
    GraphNodeUtil.connectGraphNode(this, this.mTrueNode);
    GraphNodeUtil.connectGraphNode(this, this.mFalseNode);
  }
}
