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
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.LoopBranchingNode;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.Param;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.Value;
import java.util.ArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LoopBranchingNodeImpl extends GraphNodeImpl implements LoopBranchingNode {
  protected int mLoopType;
  protected BlockGraph mLoopBody;
  protected GraphNode mConditionCheckExitNode;
  protected GraphNode mPostLoopEntryNode;
  protected GraphNode mPostLoopExitNode;
  protected GraphNode mConditionCheckEntryNode;
  protected Param mForeachLoopParam;
  protected Value mForeachLoopValue;
  public ArrayList<GraphNode> breakNodeList;
  public ArrayList<GraphNode> continueNodeList;

  @Override
  public GraphNode getConditionCheckNode() {
    return mConditionCheckEntryNode;
  }

  @Override
  public GraphNode getPostCode() {
    return mPostLoopEntryNode;
  }

  @Override
  public BlockGraph getLoopBlock() {
    return mLoopBody;
  }

  @Nullable
  @Override
  public Param getForeachIteratorParam() {
    return mForeachLoopParam;
  }

  public void setForeachIteratorParam(@NotNull Param param) {
    this.mForeachLoopParam = param;
  }

  @Nullable
  @Override
  public Value getForeachIteratorValue() {
    return mForeachLoopValue;
  }

  public void setForeachIteratorValue(@NotNull Value value) {
    this.mForeachLoopValue = value;
  }

  @Override
  public int getLoopType() {
    return this.mLoopType;
  }

  public LoopBranchingNodeImpl(BlockGraph parentGraph, int loopType) {
    super(parentGraph);
    this.mLoopType = loopType;
    breakNodeList = new ArrayList<>();
    continueNodeList = new ArrayList<>();
  }

  public void setConditionCheckEntry(GraphNode checkEntry) {
    this.mConditionCheckEntryNode = checkEntry;
  }

  public void setConditionCheckExitNode(GraphNode checkExit) {
    this.mConditionCheckExitNode = checkExit;
  }

  public void setPostLoopEntryNode(GraphNode postLoopEntry) {
    this.mPostLoopEntryNode = postLoopEntry;
  }

  public void setPostLoopExitNode(GraphNode postLoopExit) {
    this.mPostLoopExitNode = postLoopExit;
  }

  public void setLoopBody(BlockGraph loopBody) {
    this.mLoopBody = loopBody;
  }

  public void addBreak(GraphNode breakNode) {
    breakNodeList.add(breakNode);
  }

  public void addContinue(GraphNode continueNode) {
    continueNodeList.add(continueNode);
  }


  public void processBreaks() {
    //The breaks terminate the loop


    if (mLoopType == LoopBranchingNode.FOREACH_LOOP) {
      //In foreach loop. Break connects to the exit node of the body
      GraphNode bodyExit = mLoopBody.getExitNode();
      for (GraphNode breakNode : this.breakNodeList) {
        GraphNodeUtil.connectGraphNode(breakNode, bodyExit);
      }
      this.breakNodeList.clear();
    }
    else {
      //In loops other than foreach loops, they should be connected to the false branch of the
      //exit of condition check.
      //Null check
      if (mConditionCheckExitNode == null) {
        return;
      }
      GraphNode falseBranch = ((ConditionCheckNode)mConditionCheckExitNode).getFalseBranch();
      for (GraphNode breakNode : this.breakNodeList) {
        GraphNodeUtil.connectGraphNode(breakNode, falseBranch);
      }
      this.breakNodeList.clear();
    }
  }

  public void processContinues() {

    if (mLoopType == LoopBranchingNode.FOREACH_LOOP) {
      //In foreach loop, continue should be connect to the loopnode itself.
      for (GraphNode continueNode : this.continueNodeList) {
        GraphNodeUtil.connectGraphNode(continueNode, this);
      }

    }
    else {
      //the continue stmts connect to the entry of the condition check
      if (mConditionCheckEntryNode == null) {
        return;
      }

      for (GraphNode continueNode : this.continueNodeList) {
        GraphNodeUtil.connectGraphNode(continueNode, mConditionCheckEntryNode);
      }
      this.continueNodeList.clear();
    }
  }

  public void connectSpecialNodes() {
    processBreaks();
    processContinues();
  }

  @Override
  public String getSimpleName() {
    String loopTypeName = "";
    switch (this.mLoopType) {
      case LoopBranchingNode.FOR_LOOP:
        loopTypeName = "[FOR LOOP]";
        break;
      case LoopBranchingNode.WHILE_LOOP:
        loopTypeName = "[WHILE LOOP]";
        break;
      case LoopBranchingNode.DOWHILE_LOOP:
        loopTypeName = "[DOWHILE LOOP]";
        break;
      case LoopBranchingNode.FOREACH_LOOP:
        loopTypeName = "[FOREACH LOOP]";
        break;
      default:
        break;
    }
    //TODO: Add more detailed info.
    return String.format("%s", loopTypeName);
  }


}
