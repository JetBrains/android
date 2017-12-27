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
package com.android.tools.idea.experimental.codeanalysis.utils;

import com.android.tools.idea.experimental.codeanalysis.PsiCFGScene;
import com.android.tools.idea.experimental.codeanalysis.callgraph.Callgraph;
import com.android.tools.idea.experimental.codeanalysis.datastructs.PsiCFGClass;
import com.android.tools.idea.experimental.codeanalysis.datastructs.PsiCFGMethod;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.MethodGraph;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.impl.MethodGraphImpl;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.*;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.impl.ConditionFalseNode;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.impl.ConditionTrueNode;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.impl.DummyNodeImpl;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.impl.ParamImpl;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.PathManager;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Deque;
import java.util.Map;
import java.util.Set;

public class CFGUtil {

  public static final String NODE_TYPE_GRAPHNODE = "GraphNode";
  public static final String NODE_TYPE_BLOCKENTRY = "BlockGraphEntryNode";
  public static final String NODE_TYPE_BLOCKEXIT = "BlockGraphExitNode";
  public static final String NODE_TYPE_CONDITION_CHECK_NODE = "ConditionCheckNode";
  public static final String NODE_TYPE_CONDITION_TRUE_NODE = "ConditionTrueNode";
  public static final String NODE_TYPE_CONDITION_FALSE_NODE = "ConditionFalseNode";
  public static final String NODE_TYPE_UNREACHABLE_ENTRY_NODE = "UnreachableEntryNode";
  public static final String NODE_TYPE_IF_BRANCHING_NODE = "IfBranchingNode";
  public static final String NODE_TYPE_LOOP_BRANCHING_NODE = "LoopBranchingNode";

  private static int nodeCounter = 0;

  public static MethodGraph constructControlFlowGraphFromCodeBlock(@NotNull PsiCodeBlock cb) {
    MethodGraph cfg = null;
    return cfg;
  }

  public static MethodGraph constructMethodGraph(@NotNull PsiCFGScene scene,
                                                 @NotNull PsiCodeBlock psiCodeBlock,
                                                 @NotNull PsiCFGMethod cfgMethod) {

    PsiMethod psiMethodRef = cfgMethod.getMethodRef();
    PsiParameterList pl = psiMethodRef.getParameterList();

    MethodGraphImpl retGraph = new MethodGraphImpl(cfgMethod);

    //Process the params
    processParameters(scene, pl, retGraph);

    //Traverse the code block
    CFGBuilder builder = new CFGBuilder(scene, retGraph, cfgMethod.getDeclaringClass(), psiCodeBlock);
    builder.build();


    return retGraph;
  }

  public static MethodGraph constructMethodGraphForLambda(@NotNull PsiCFGScene scene, @NotNull PsiCFGMethod lambdaMethod) {
    PsiElement lambdaBody = lambdaMethod.getBody();
    MethodGraphImpl lambdaGraph = new MethodGraphImpl(lambdaMethod);
    PsiLambdaExpression lambdaPsiRef = lambdaMethod.getLambdaRef();
    if (lambdaPsiRef == null) {
      PsiCFGDebugUtil.LOG.warning("Method: " + lambdaMethod + " is not recognized as a lambda");
      return null;
    }

    //Process lambda's params
    processLambdaParameters(scene, lambdaPsiRef.getParameterList(), lambdaGraph);
    CFGBuilder builder = null;
    if (lambdaBody instanceof PsiCodeBlock) {
      //The body of the lambda is a PsiCodeBlock
      builder = new CFGBuilder(
        scene, lambdaGraph, lambdaMethod.getDeclaringClass(), (PsiCodeBlock)lambdaBody);
      builder.setLambdaFlag();
    }
    else if (lambdaBody instanceof PsiExpression) {
      builder = new CFGBuilder(
        scene, lambdaGraph, lambdaMethod.getDeclaringClass(), (PsiExpression)lambdaBody);
      builder.setLambdaFlag();
    } else {
      PsiCFGDebugUtil.LOG.warning("Method: " + lambdaPsiRef.getText() + " has unknown body of type " +
                                  ((lambdaBody == null) ? "null" : lambdaBody.getClass().getName()));
      return null;
    }

    builder.build();
    lambdaMethod.setControlFlowGraph(lambdaGraph);
    return lambdaGraph;
  }

  private static void processLambdaParameters(@NotNull PsiCFGScene scene,
                                              @NotNull PsiParameterList pl,
                                              @NotNull MethodGraphImpl lambdaGraph) {
    if (pl.getParametersCount() != 0) {
      PsiParameter[] allParams = pl.getParameters();
      for (PsiParameter curPsiParam : allParams) {
        if (curPsiParam == null) {
          PsiCFGDebugUtil.LOG.warning("null found in param in " +
                                      lambdaGraph.getPsiCFGMethod().getLambdaRef().getText());
        }
        else {
          ParamImpl curParamImpl = new ParamImpl(curPsiParam);
          lambdaGraph.addParam(curPsiParam, curParamImpl);
        }
      }
    }
  }

  private static void processParameters(@NotNull PsiCFGScene scene, @NotNull PsiParameterList pl, @NotNull MethodGraphImpl retGraph) {

    PsiMethod psiMethodRef = retGraph.getPsiCFGMethod().getMethodRef();
    if (pl == null) {
      PsiCFGDebugUtil.LOG.info("ParamList is null in this method " + psiMethodRef.getName());
    }
    if (pl.getParametersCount() == 0) {
      //PsiCFGDebugUtil.LOG.info("ParamList size 0. No param in this method " + psiMethodRef.getName());
    }
    else {
      //Param size is not 0
      PsiParameter[] allParams = pl.getParameters();
      //For each param, we should create a wrapper object for it.
      for (PsiParameter curPsiParam : allParams) {
        if (curPsiParam == null) {
          PsiCFGDebugUtil.LOG.warning("PsiParam in method " + psiMethodRef.getName() + " is null");
        }
        else {
          ParamImpl curParamImpl = new ParamImpl(curPsiParam);
          retGraph.addParam(curPsiParam, curParamImpl);
        }
      }
    }
  }

  public static void outputCallGraphDotFile(Callgraph cg) {
    String path = PathManager.getLogPath();
    String fileName = "CallGraph.dot";
    BufferedWriter bw = null;
    try {
      File dotFile = new File(path, fileName);
      if (dotFile.exists()) {
        dotFile.delete();
      }
      dotFile.createNewFile();
      PsiCFGDebugUtil.LOG.info("Log Callgraph to file: " + dotFile.getAbsolutePath());
      FileWriter fw = new FileWriter(dotFile);
      bw = new BufferedWriter(fw);

      Map<PsiCFGMethod, Integer> allNodes = getAllMethodNodesFromCallGraph(cg);
      bw.write("digraph G{\n");
      for (PsiCFGMethod method : allNodes.keySet()) {
        String label = method.getName();
        String line = String.format("n%d[label=\"%s\"];\n", allNodes.get(method), label);
        bw.write(line);
      }

      bw.write("\n");

      for (PsiCFGMethod curMethod : cg.callerMethodToCalleeMethodMap.keySet()) {
        Integer sId = allNodes.get(curMethod);
        for (PsiCFGMethod tgtMethod : cg.callerMethodToCalleeMethodMap.get(curMethod)) {
          Integer tId = allNodes.get(tgtMethod);
          String line = String.format("n%d -> n%d;\n", sId, tId);
          bw.write(line);
        }
      }
      bw.write("\n");

      bw.write("}");

      //Output digraphG


    }
    catch (IOException ioe) {
      ioe.printStackTrace();
    }
    finally {
      try {
        if (bw != null) {
          bw.close();
        }
      }
      catch (Exception ex) {

      }
    }
  }

  private static Map<PsiCFGMethod, Integer> getAllMethodNodesFromCallGraph(Callgraph cg) {
    Map<PsiCFGMethod, Integer> retMap = Maps.newHashMap();
    Set<PsiCFGMethod> allMethods = cg.allMethodsInGraph;
    int i = 0;
    for (PsiCFGMethod curMethod : allMethods) {
      retMap.put(curMethod, i);
      i++;
    }
    return retMap;
  }

  public static void outputCFGDotFile(MethodGraph graph) {
    String path = PathManager.getLogPath();
    //PsiMethod psiMethod = (PsiMethod) graph.getPsiCFGMethod().getPsiRef();
    String methodName = graph.getPsiCFGMethod().getName();
    String className = "dummyClass";
    //PsiElement parentElement = psiMethod.getParent();
    PsiCFGClass cfgClass = graph.getPsiCFGMethod().getDeclaringClass();
    //if (parentElement instanceof PsiClass) {
    //  PsiClass mClass = graph.getPsiCFGMethod().getDeclaringClass().getPsiClass();
    //  className = mClass.getQualifiedName().replace("." , "-");
    //}
    className = cfgClass.getQualifiedClassName().replace(".", "-");
    String fileName = className + "-" + methodName + ".dot";
    BufferedWriter bw = null;
    try  {
      File dotFile = new File(path, fileName);
      if (dotFile.exists()) {
        dotFile.delete();
      }
      dotFile.createNewFile();
      PsiCFGDebugUtil.LOG.info("Log CFG to file: " + dotFile.getAbsolutePath());
      FileWriter fw = new FileWriter(dotFile);
      bw = new BufferedWriter(fw);
      Map<GraphNode, Integer> allNodes = getAllNodeFromGraph(graph);
      //Output digraph G
      bw.write("digraph G {\n");
      for (GraphNode node : allNodes.keySet()) {
        String label = getLabelFromGraphNode(node);
        String line = String.format("n%d[label=\"%s\"];\n", allNodes.get(node), label);
        bw.write(line);
      }
      bw.write("\n");
      bfsOutputEdges(bw, graph.getEntryNode(), allNodes);

      bw.write("}");

    }
    catch (IOException ioe) {
      ioe.printStackTrace();
    }
    finally {
      try {
        if (bw != null) {
          bw.close();
        }
      }
      catch (Exception ex) {

      }
    }
  }

  private static Map<GraphNode, Integer> getAllNodeFromGraph(MethodGraph graph) {
    Map<GraphNode, Integer> retMap = Maps.newHashMap();
    retMap.put(graph.getEntryNode(), 0);
    retMap.put(graph.getExitNode(), 1);
    nodeCounter = 2;
    for (GraphNode initNode : graph.getEntryNode().getOut()) {
      dfsGetAllNodes(retMap, initNode);
    }
    return retMap;
  }

  private static void dfsGetAllNodes(Map<GraphNode, Integer> retMap, GraphNode curNode) {
    if (retMap.containsKey(curNode)) {
      return;
    }
    retMap.put(curNode, nodeCounter);
    nodeCounter++;
    for (GraphNode nextNode : curNode.getOut()) {
      dfsGetAllNodes(retMap, nextNode);
    }
  }

  private static void bfsOutputEdges(BufferedWriter bw, GraphNode entry, Map<GraphNode, Integer> NodeMap) throws IOException {

    Set<GraphNode> vistedNode = Sets.newHashSet();
    Deque<GraphNode> workList = Queues.newArrayDeque();
    workList.add(entry);

    while (!workList.isEmpty()) {
      GraphNode node = workList.removeFirst();
      if (vistedNode.contains(node)) {
        continue;
      }
      vistedNode.add(node);
      Integer srcID = NodeMap.get(node);
      GraphNode[] outEdges = node.getOut();
      for (GraphNode outNode : outEdges) {
        Integer tgtID = NodeMap.get(outNode);
        String line = String.format("n%d -> n%d;\n", srcID, tgtID);
        bw.write(line);
        if (!vistedNode.contains(outNode)) {
          workList.addLast(outNode);
        }
      }
    }
  }

  public static String getLabelFromGraphNode(GraphNode node) {
    String nodeType = "GraphNode";
    if (node instanceof BlockGraphEntryNode) {
      nodeType = NODE_TYPE_BLOCKENTRY;
    }
    else if (node instanceof BlockGraphExitNode) {
      nodeType = NODE_TYPE_BLOCKEXIT;
    }
    else if (node instanceof IfBranchingNode) {
      nodeType = NODE_TYPE_IF_BRANCHING_NODE;
    }
    else if (node instanceof ConditionCheckNode) {
      nodeType = NODE_TYPE_CONDITION_CHECK_NODE;
    }
    else if (node instanceof ConditionTrueNode) {
      nodeType = NODE_TYPE_CONDITION_TRUE_NODE;
    }
    else if (node instanceof ConditionFalseNode) {
      nodeType = NODE_TYPE_CONDITION_FALSE_NODE;
    }
    else if (node instanceof DummyNodeImpl) {
      nodeType = NODE_TYPE_UNREACHABLE_ENTRY_NODE;
    }
    else if (node instanceof LoopBranchingNode) {
      nodeType = NODE_TYPE_LOOP_BRANCHING_NODE;
    }
    String stmtString = node.getSimpleName();
    return nodeType + "\\n" + stmtString;
  }
}

