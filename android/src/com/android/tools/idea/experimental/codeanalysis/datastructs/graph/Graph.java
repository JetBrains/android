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
package com.android.tools.idea.experimental.codeanalysis.datastructs.graph;

import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.GraphNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The base interface for a graph.
 * Unless the Graph is used to represent a conditional check expression,
 * It should only contain 1 exit node.
 */
public interface Graph {

  /**
   * Return the placeholder entry node of
   * this graph.
   * @return Return the Entry Node
   */
  @NotNull
  GraphNode getEntryNode();

  /**
   * Return the placeholder exit node of
   * this graph.
   * @return Return the Exit Node.
   */
  @NotNull
  GraphNode getExitNode();

  /**
   * Return The parent graph of this graph. It can be null, which means it does not exist.
   * @return The parent graph.
   */
  @Nullable
  Graph getParentGraph();

  boolean hasMultipleExit();

  GraphNode[] getAllExits();

  GraphNode[] EMPTY_ARRAY = GraphNode.EMPTY_ARRAY;
}
