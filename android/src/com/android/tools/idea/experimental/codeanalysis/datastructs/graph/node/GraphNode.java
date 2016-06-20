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

/**
 * Created by haowei on 6/7/16.
 */
public interface GraphNode {
  GraphNode[] getIn();

  GraphNode[] getOut();

  void addIn(GraphNode target);

  void addOut(GraphNode target);

  void removeIn(GraphNode target);

  void removeOut(GraphNode target);

  public Stmt[] getStatements();

  public Graph getParentGraph();

  public String getSimpleName();

  public boolean containsInvocation();

  //Avoid multiple initiation
  static final GraphNode[] EMPTY_ARRAY = new GraphNode[0];
}
