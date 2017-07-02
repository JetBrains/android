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
import com.android.tools.idea.experimental.codeanalysis.datastructs.stmt.Stmt;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Set;

public class GraphNodeImpl implements GraphNode {

  protected BlockGraph mBlockGraph;
  protected Set<GraphNode> mInNodes;
  protected Set<GraphNode> mOutNodes;
  protected ArrayList<Stmt> mStmtList;
  protected boolean mContainsInvoke;

  public static GraphNodeImpl[] EMPTY_ARRAY = new GraphNodeImpl[0];

  @Override
  public Stmt[] getStatements() {
    return mStmtList.toArray(Stmt.EMPTY_ARRAY);
  }

  @Override
  public BlockGraph getParentGraph() {
    return this.mBlockGraph;
  }

  @Override
  public String getSimpleName() {
    StringBuilder sb = new StringBuilder();
    if (this.mStmtList != null && !this.mStmtList.isEmpty()) {
      for (Stmt stmt : this.mStmtList) {
        sb.append(stmt.getSimpleName() + "\\n");
      }
    }
    else {
      sb.append("Statement: 0");
    }
    return sb.toString();
  }

  @Override
  public boolean containsInvocation() {
    return mContainsInvoke;
  }

  public void setInvocation() {
    this.mContainsInvoke = true;
  }

  @Override
  public GraphNode[] getIn() {
    return this.mInNodes.toArray(GraphNode.EMPTY_ARRAY);
  }

  @Override
  public GraphNode[] getOut() {
    return this.mOutNodes.toArray(GraphNode.EMPTY_ARRAY);
  }

  @Override
  public void addIn(@NotNull GraphNode target) {
    this.mInNodes.add(target);
  }

  @Override
  public void addOut(@NotNull GraphNode target) {
    this.mOutNodes.add(target);
  }

  @Override
  public void removeIn(@NotNull GraphNode target) {
    this.mInNodes.remove(target);
  }


  public ArrayList<Stmt> getStmtList() {
    return this.mStmtList;
  }

  @Override
  public void removeOut(GraphNode target) {
    this.mOutNodes.remove(target);
  }

  public GraphNodeImpl(BlockGraph parentGraph) {
    this.mBlockGraph = parentGraph;
    this.mInNodes = Sets.newHashSet();
    this.mOutNodes = Sets.newHashSet();
    this.mStmtList = Lists.newArrayList();
    this.mContainsInvoke = false;
  }

  /**
   * Empty Constructor.
   * Allow sub class to perform some hack.
   */
  public GraphNodeImpl() {

  }
}
