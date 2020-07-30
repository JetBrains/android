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
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.Param;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The LoopBranchingNode itself should be a placeholder node
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
 */
public interface LoopBranchingNode extends GraphNode {
  /*Only available when the looping is for loop*/

  /**
   * Return the last condition check code
   * Return null if it is a foreach loop.
   * @return
   */
  @Nullable
  public GraphNode getConditionCheckNode();

  /**
   * for (initExpr; condExpr; postExpr);
   * Return the nodes that evaluate the postExpr.
   * Return null if it is not a for loop.
   * @return The node for the post code.
   */
  @Nullable
  public GraphNode getPostCode();

  /**
   * Return the loop body.
   * @return The CFG for the loop body
   */
  @NotNull
  public BlockGraph getLoopBlock();

  /**
   * for (iterParam : iterValue)
   * return the Param wrapper of "iterParam"
   * return null if it is not a foreach loop.
   * @return The param wrapper.
   */
  @Nullable
  public Param getForeachIteratorParam();

  /**
   * for (iterParam : iterValue)
   * return the Value of "iterValue"
   * return null if it is not a foreach loop.
   * @return The value wrapper.
   */
  @Nullable
  public Value getForeachIteratorValue();

  /**
   * Return the loop type of this loop.
   * It can be
   * LoopBranchingNode.FOR_LOOP
   * LoopBranchingNode.WHILE_LOOP
   * LoopBranchingNode.DOWHILE_LOOP
   * LoopBranchingNode.FOREACH_LOOP
   *
   * The return value should be changed to enum type in the future.
   * @return The loop type.
   */
  public int getLoopType();

  public static final int FOR_LOOP = 0x00;
  public static final int WHILE_LOOP = 0x01;
  public static final int DOWHILE_LOOP = 0x02;
  public static final int FOREACH_LOOP = 0x03;
}
