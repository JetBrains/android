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
package com.android.tools.idea.experimental.codeanalysis.callgraph;

import com.android.tools.idea.experimental.codeanalysis.datastructs.PsiCFGMethod;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.Graph;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.MethodGraph;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.GraphNode;
import com.google.common.base.Supplier;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;

import java.util.Collection;
import java.util.Set;

/**
 * Callgraph class which store the call graph
 * built from the Callgraph Builder
 */
public class Callgraph {

  //A map from GraphNode that contains an invocation statement to the target PsiCFGMethods.
  public Multimap<GraphNode, PsiCFGMethod> callerNodeToMethodsMap;

  //A map from the taret PsiCFGMethod BACK to the invocation GraphNode.
  public Multimap<PsiCFGMethod, GraphNode> calleeMethodToCallerGraphNodeMap;

  //Callee GraphNode is the EntryNode
  //A map from GraphNode that contains the invocation statement to the GraphNodes which are the
  //EntryNode of target methods.
  public Multimap<GraphNode, GraphNode> callerNodeToCalleeNodeMap;

  //Callee GraphNode here is the ExitNode
  //A map from GraphNode that is the ExitNode of the target method back to the GraphNodes that
  //contains the invocation statements.
  public Multimap<GraphNode, GraphNode> calleeNodeToCallerNodeMap;

  //A map from the method that contains the invocation statement to the target methods.
  public Multimap<PsiCFGMethod, PsiCFGMethod> callerMethodToCalleeMethodMap;

  //A map from the target method back to the methods that contains the invocation.
  public Multimap<PsiCFGMethod, PsiCFGMethod> calleeMethodToCallerMethodReturnMap;


  public PsiCFGMethod[] findCalleeMethodForGraphNode(GraphNode node) {
    if (callerNodeToMethodsMap.containsKey(node)) {
      Collection<PsiCFGMethod> methodCollection = callerNodeToMethodsMap.get(node);
      return methodCollection.toArray(PsiCFGMethod.EMPTY_ARRAY);
    }

    return PsiCFGMethod.EMPTY_ARRAY;
  }

  public GraphNode[] findCalleeGraphNodeForGraphNode(GraphNode node) {
    if (callerNodeToCalleeNodeMap.containsKey(node)) {
      Collection<GraphNode> graphNodeCollection = callerNodeToCalleeNodeMap.get(node);
      return graphNodeCollection.toArray(GraphNode.EMPTY_ARRAY);
    }

    return GraphNode.EMPTY_ARRAY;
  }

  public PsiCFGMethod[] findCalleeForMethod(PsiCFGMethod method) {
    if (calleeMethodToCallerMethodReturnMap.containsKey(method)) {
      Collection<PsiCFGMethod> methodsCollection = calleeMethodToCallerMethodReturnMap.get(method);
      return methodsCollection.toArray(PsiCFGMethod.EMPTY_ARRAY);
    }

    return PsiCFGMethod.EMPTY_ARRAY;
  }

  public PsiCFGMethod getNodesParentMethod(GraphNode node) {
    Graph parentGraph = node.getParentGraph();
    while (parentGraph != null && (!(parentGraph instanceof MethodGraph))) {
      parentGraph = parentGraph.getParentGraph();
    }
    if (parentGraph == null) {
      return null;
    } else {
      return ((MethodGraph)parentGraph).getPsiCFGMethod();
    }
  }

  public GraphNode[] findCallerForMethod(PsiCFGMethod method) {
    return GraphNode.EMPTY_ARRAY;
  }

  public Set<PsiCFGMethod> allMethodsInGraph;

  protected Callgraph() {
    this.callerNodeToMethodsMap = Multimaps.newSetMultimap(
      Maps.newHashMap(), new Supplier<Set<PsiCFGMethod>>() {
        @Override
        public Set<PsiCFGMethod> get() {
          return Sets.newHashSet();
        }
      }
    );

    this.calleeMethodToCallerGraphNodeMap = Multimaps.newSetMultimap(
      Maps.newHashMap(), new Supplier<Set<GraphNode>>() {
        @Override
        public Set<GraphNode> get() {
          return Sets.newHashSet();
        }
      }
    );

    this.callerNodeToCalleeNodeMap = Multimaps.newSetMultimap(
      Maps.newHashMap(), new Supplier<Set<GraphNode>>() {
        @Override
        public Set<GraphNode> get() {
          return Sets.newHashSet();
        }
      }
    );

    this.calleeNodeToCallerNodeMap = Multimaps.newSetMultimap(
      Maps.newHashMap(), new Supplier<Set<GraphNode>>() {
        @Override
        public Set<GraphNode> get() {
          return Sets.newHashSet();
        }
      }
    );

    this.callerMethodToCalleeMethodMap = Multimaps.newSetMultimap(
      Maps.newHashMap(), new Supplier<Set<PsiCFGMethod>>() {
        @Override
        public Set<PsiCFGMethod> get() {
          return Sets.newHashSet();
        }
      }
    );

    this.calleeMethodToCallerMethodReturnMap = Multimaps.newSetMultimap(
      Maps.newHashMap(), new Supplier<Set<PsiCFGMethod>>() {
        @Override
        public Set<PsiCFGMethod> get() {
          return Sets.newHashSet();
        }
      }
    );
    allMethodsInGraph = Sets.newHashSet();

  }

}
