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

import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.Graph;
import com.android.tools.idea.experimental.codeanalysis.datastructs.stmt.Stmt;
import org.jetbrains.annotations.NotNull;

/**
 * Base for all nodes in a CFG.
 */
public interface GraphNode {

  /**
   * Get the source nodes of all incoming edges
   * @return A new array of incoming nodes
   */
  @NotNull
  GraphNode[] getIn();

  /**
   * Get the target nodes of all outbound edges
   * @return A new array of outbound nodes
   */
  @NotNull
  GraphNode[] getOut();

  /**
   * Add the source node of an incoming edge
   * It is OK to add same node for multiple times.
   * @param target The source node
   */
  void addIn(@NotNull GraphNode target);

  /**
   * Add the target node of an outbound edge
   * It is OK to add same node for multiple times.
   * @param target The target node
   */
  void addOut(@NotNull GraphNode target);

  /**
   * Remove a node from the source nodes of incoming edges.
   * Will not throw anything if the node does not exist.
   * @param target The node that should be removed.
   */
  void removeIn(@NotNull GraphNode target);

  /**
   * Remove a node from the target nodes of outbound edges.
   * Will not throw anything if the node does not exist.
   * @param target The node that should be removed.
   */
  void removeOut(@NotNull GraphNode target);

  /**
   * Get the statements stored in this node.
   * Currently there should be no more than 1 statements
   * in the return value.
   * @return A new array of statments.
   */
  @NotNull
  public Stmt[] getStatements();

  /**
   * Get the graph that contains this node.
   * @return The graph.
   */
  @NotNull
  public Graph getParentGraph();

  /**
   * Get a short string that can describe this node.
   * @return A string of description of this node.
   */
  @NotNull
  public String getSimpleName();

  /**
   * Check if this node contains an invocation statement.
   * @return Return true if the statement contains invocation.
   */
  public boolean containsInvocation();

  static final GraphNode[] EMPTY_ARRAY = new GraphNode[0];
}
