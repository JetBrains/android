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
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.SwitchCaseGraph;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.GraphNode;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.SwitchBranchingNode;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.Value;
import com.google.common.collect.Maps;

import java.util.Map;
import java.util.Set;

public class SwitchBranchingNodeImpl extends GraphNodeImpl implements SwitchBranchingNode {

  protected Value mCheckedValue;
  protected Map<Value, GraphNode> mSwitchTable;
  protected GraphNode mDefaultTarget;
  protected SwitchCaseGraph mCaseGraph;


  @Override
  public GraphNode[] getOut() {
    GraphNode[] retArray = new GraphNode[mSwitchTable.size()];
    int i = 0;
    for (Value v : mSwitchTable.keySet()) {
      GraphNode target = mSwitchTable.get(v);
      retArray[i] = target;
      i++;
    }
    return retArray;
  }

  @Override
  public Value getCheckedValue() {
    return mCheckedValue;
  }

  @Override
  public GraphNode getDefaultTarget() {
    return mDefaultTarget;
  }

  @Override
  public void setDefaultTarget(GraphNode target) {
    this.mDefaultTarget = target;
  }

  @Override
  public Value[] getKeys() {
    Set<Value> keySet = mSwitchTable.keySet();
    return keySet.toArray(Value.EMPTY_ARRAY);
  }

  @Override
  public GraphNode getTargetViaKey(Value key) {
    if (mSwitchTable.containsKey(key)) {
      return mSwitchTable.get(key);
    }
    return null;
  }

  public void setCheckedValue(Value checkedValue) {
    this.mCheckedValue = checkedValue;
  }

  @Override
  public void setTargetViaKey(Value key, GraphNode target) {
    mSwitchTable.put(key, target);
    target.addIn(this);
  }

  @Override
  public void setSwitchCaseGraph(SwitchCaseGraph graph) {
    this.mCaseGraph = graph;
  }

  @Override
  public SwitchCaseGraph getSwitchCaseGraph() {
    return this.mCaseGraph;
  }

  @Override
  public String getSimpleName() {
    return "SwitchNode";
  }

  public SwitchBranchingNodeImpl(BlockGraph graph) {
    super(graph);
    this.mSwitchTable = Maps.newHashMap();
  }
}
