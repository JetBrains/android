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
package com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node;

import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.BlockGraph;

/**
 * The LoopBranchingNode itself should be a dummy node
 * The initialization in the for loop should be inserted
 * before this node.
 * For the "for loop" and "while", the out edge's target
 * should be the entry of the condition check code. For the
 * do while loop, the out edge's target is the EntryNode of the loop
 * body.
 * In the for loop, post loop statements will be inserted after the ExitNode of the Loopbody
 * And then connected to the condition check code.
 * In the while loop, there is no post loop statements, therefore the ExitNode of the Loopbody
 * is always connect to the entry of the condition check node.
 * For the GraphNode after the LoopNode, its In Edge's source should always be the last condition
 * check code.
 *
 * Created by haowei on 6/13/16.
 */
public interface LoopBranchingNode extends GraphNode {
  /*Only available when the looping is for loop*/

  /**
   * Return the last condition check code
   *
   * @return
   */
  public GraphNode getConditionCheckNode();

  /*Only available in for loop*/
  public GraphNode getPostCode();

  public BlockGraph getLoopBlock();

  /*Maybe you should use enum types*/
  public int getLoopType();

  public static final int FOR_LOOP = 0x00;
  public static final int WHILE_LOOP = 0x01;
  public static final int DOWHILE_LOOP = 0x02;
}
