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

import com.android.tools.idea.experimental.codeanalysis.PsiCFGScene;
import com.android.tools.idea.experimental.codeanalysis.datastructs.PsiCFGClass;
import com.android.tools.idea.experimental.codeanalysis.datastructs.PsiCFGMethod;
import com.android.tools.idea.experimental.codeanalysis.datastructs.PsiCFGPartialMethodSignature;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.Graph;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.MethodGraph;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.GraphNode;
import com.android.tools.idea.experimental.codeanalysis.datastructs.stmt.AssignStmt;
import com.android.tools.idea.experimental.codeanalysis.datastructs.stmt.Stmt;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.*;
import com.android.tools.idea.experimental.codeanalysis.utils.PsiCFGAnalysisUtil;
import com.android.tools.idea.experimental.codeanalysis.utils.PsiCFGDebugUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

/**
 * A call graph builder.
 * It based on the results of the intraprocedural CFG
 * generated in stage 3.
 */
public class CallgraphBuilder {

  protected PsiCFGScene mScene;

  protected PsiCFGAnalysisUtil mAnalysisUtil;

  protected Callgraph mCallGraphInstance;

  protected Map<Pair<PsiCFGClass, PsiCFGPartialMethodSignature>, Set<PsiCFGMethod>> mMethodOrderTreeMap;

  public PsiCFGClass JAVA_LANG_OBJECT;

  public CallgraphBuilder(@NotNull PsiCFGScene scene, @NotNull PsiCFGAnalysisUtil analysisUtil) {
    this.mScene = scene;
    this.mAnalysisUtil = analysisUtil;
    this.mMethodOrderTreeMap = Maps.newHashMap();
    JAVA_LANG_OBJECT = mScene.getPsiCFGClass("java.lang.Object");
  }

  public void build() {
    //Initiate

    this.mCallGraphInstance = new Callgraph();

    //Retrive all callsites

    GraphNode[] invocationNodes = mScene.getAllInvocationNode();
    for (GraphNode invocationNode : invocationNodes) {

      processSingleInvocation(invocationNode);
    }
  }

  /**
   * Create call graph edges for a single invocation site
   *
   * @param node The node in CFG that contains an invocation
   *             statement.
   */
  public void processSingleInvocation(GraphNode node) {
    Stmt[] stmtWithInvocationArray = node.getStatements();

    if (stmtWithInvocationArray.length != 1) {
      //Just skip this node if more than 1 stmt is found.
      //For all node that contains invocation
      //there should be only 1 statement
      PsiCFGDebugUtil.LOG.warning("Node contains more than 1 stmt" + node.getSimpleName());
      return;
    }

    Stmt stmtWithInvocation = stmtWithInvocationArray[0];

    if (stmtWithInvocation instanceof AssignStmt) {
      Value Rop = ((AssignStmt)stmtWithInvocation).getROp();
      if (Rop instanceof InvokeExpr) {
        //It is a invokeExpr
        processSingleInvocationWithInvokeWxpr(node, (InvokeExpr)Rop);
      }
      else if (Rop instanceof NewExpr) {
        //It is a constructor invocation
        processSingleInvocationWithConstructorInvoke(node, (NewExpr)Rop);
      }
      else {
        //Unhandled invocation
        PsiCFGDebugUtil.LOG.warning("Invocation neither an InvokeExpr nor NewExpr: "
                                    + Rop.getSimpleName());
      }
    }
  }

  /**
   * Retrieve the method that contains this node
   *
   * @param node The node that needs to find its parent method
   * @return
   */
  public PsiCFGMethod retrieveParentMethod(GraphNode node) {
    Graph parentGraph = node.getParentGraph();

    while ((parentGraph != null) && (!(parentGraph instanceof MethodGraph))) {
      parentGraph = parentGraph.getParentGraph();
    }
    if (parentGraph != null) {
      MethodGraph methodGraph = (MethodGraph)parentGraph;
      return methodGraph.getPsiCFGMethod();
    }
    else {
      return null;
    }
  }

  /**
   * Add a single target method to the call graph
   *
   * @param callerNode   The node that contains invocation statement
   * @param calleeMethod The target method of this invocation
   */
  public void addToCallGraph(GraphNode callerNode, PsiCFGMethod calleeMethod) {
    mCallGraphInstance.callerNodeToMethodsMap.put(callerNode, calleeMethod);
    mCallGraphInstance.calleeMethodToCallerGraphNodeMap.put(calleeMethod, callerNode);
    PsiCFGMethod callerMethod = retrieveParentMethod(callerNode);
    if (callerMethod != null) {
      mCallGraphInstance.callerMethodToCalleeMethodMap.put(callerMethod, calleeMethod);
      mCallGraphInstance.calleeMethodToCallerMethodReturnMap.put(calleeMethod, callerMethod);
      mCallGraphInstance.allMethodsInGraph.add(callerMethod);
      mCallGraphInstance.allMethodsInGraph.add(calleeMethod);
    }

    if (calleeMethod.getControlFlowGraph() != null) {
      GraphNode entryNode = calleeMethod.getControlFlowGraph().getEntryNode();
      GraphNode exitNode = calleeMethod.getControlFlowGraph().getExitNode();

      mCallGraphInstance.callerNodeToCalleeNodeMap.put(callerNode, entryNode);
      mCallGraphInstance.calleeNodeToCallerNodeMap.put(exitNode, callerNode);
    }
  }

  public void performCHAForInvocationSite(GraphNode node, PsiType receiverType, PsiCFGMethod targetMethod) {
    //Only Object can perform instance invoke
    if (!(receiverType instanceof PsiClassType)) {
      //The reciever type is not an object
      PsiCFGDebugUtil.LOG.warning("The Receiver's type is not PsiClassType "
                                  + receiverType.getCanonicalText() + "  "
                                  + targetMethod.getName());
    }
    else {
      PsiClassType receiverClassType = (PsiClassType)receiverType;
      PsiClass psiClassRef = receiverClassType.resolve();
      PsiCFGClass receiverClass = mScene.getPsiCFGClass(psiClassRef);
      if (receiverClass == null) {
        PsiCFGDebugUtil.LOG.warning("The Receiver's CFGClass is not resolved during " +
                                    "the CFG construction " + psiClassRef.getQualifiedName());
        return;
      }

      //Find first concrete method to the top
      //It may not exist
      PsiCFGPartialMethodSignature methodSignature = targetMethod.getSignature();
      PsiCFGMethod nearestConcreteMethodFromTop = getNearestConcreteMethod(receiverClass, methodSignature);
      if (nearestConcreteMethodFromTop != null) {
        addToCallGraph(node, nearestConcreteMethodFromTop);
      }

      //Find concrete method to the leaf
      ArrayList<PsiCFGMethod> methodList = Lists.newArrayList();
      recursivelyQueryConcreteMethodFromChildrenWithCache(methodList, receiverClass, methodSignature);
      for (PsiCFGMethod concreteMethodFromSubClass : methodList) {
        addToCallGraph(node, concreteMethodFromSubClass);
      }
    }
  }

  public void recursivelyQueryConcreteMethodFromChildrenWithCache(
    ArrayList<PsiCFGMethod> methodList, PsiCFGClass receiverClass, PsiCFGPartialMethodSignature signature) {
    Pair<PsiCFGClass, PsiCFGPartialMethodSignature> keyPair = new Pair<>(receiverClass, signature);
    if (mMethodOrderTreeMap.containsKey(keyPair)) {
      methodList.addAll(mMethodOrderTreeMap.get(keyPair));
    }
    else {
      recursivelyQueryConcreteMethodFromChildrenWithOutCache(methodList, receiverClass, signature);
      mMethodOrderTreeMap.put(keyPair, Sets.newHashSet(methodList));
    }
  }

  public void recursivelyQueryConcreteMethodFromChildrenWithOutCache(
    ArrayList<PsiCFGMethod> methodList, PsiCFGClass receiverClass, PsiCFGPartialMethodSignature signature) {
    Pair<PsiCFGClass, PsiCFGPartialMethodSignature> keyPair = new Pair<>(receiverClass, signature);
    PsiCFGMethod method = receiverClass.getMethod(signature);
    if (method != null && (!method.isAbstract())) {
      methodList.add(method);
    }

    //Go through sub classes and interfaces
    for (PsiCFGClass subClass : receiverClass.getSubClassSet()) {
      if (mMethodOrderTreeMap.containsKey(keyPair)) {
        methodList.addAll(mMethodOrderTreeMap.get(keyPair));
      }
      else {
        recursivelyQueryConcreteMethodFromChildrenWithOutCache(methodList, subClass, signature);
      }
    }
  }

  public void addInvokeExprWithThisRef(GraphNode node, PsiType thisBaseType, PsiCFGMethod method) {
    if (!method.isAbstract()) {
      addToCallGraph(node, method);
    }
    else {
      PsiClassType classType = null;
      PsiCFGClass cfgClass = null;
      if (thisBaseType instanceof PsiClassType) {
        classType = (PsiClassType)thisBaseType;
        cfgClass = mScene.getPsiCFGClass(classType.resolve());
      }
      else {
        PsiCFGDebugUtil.LOG.warning("PsiType of ThisRef is not a PsiClassType :"
                                    + thisBaseType.getClass().getSimpleName());
        return;
      }
      if (cfgClass == null) {
        PsiCFGDebugUtil.LOG.warning("PsiType of ThisRef cannot be resolved to cfgClass :"
                                    + thisBaseType.getClass().getSimpleName());
      }

      ArrayList<PsiCFGMethod> methodsList = Lists.newArrayList();
      recursivelyQueryConcreteMethodFromChildrenWithCache(methodsList, cfgClass, method.getSignature());
    }
  }

  public PsiCFGMethod getNearestConcreteMethod(PsiCFGClass receiverClass, PsiCFGPartialMethodSignature signature) {
    while (receiverClass != null && !receiverClass.equals(JAVA_LANG_OBJECT)) {
      PsiCFGMethod methodInCurClass = receiverClass.getMethod(signature);
      if (methodInCurClass != null && (!methodInCurClass.isAbstract())) {
        return methodInCurClass;
      }
      receiverClass = receiverClass.getSuperClass();
    }
    return null;
  }

  public void processSingleInvocationWithInvokeWxpr(GraphNode node, InvokeExpr invokeExpr) {

    if (invokeExpr instanceof StaticInvokeExpr) {
      //Only 1 target
      addToCallGraph(node, invokeExpr.getMethod());
    }
    else if (invokeExpr instanceof InstanceInvokeExpr) {
      InstanceInvokeExpr instanceInvokeExpr = (InstanceInvokeExpr)invokeExpr;
      Value base = instanceInvokeExpr.getBase();

      PsiType baseType = base.getType();
      PsiCFGMethod targetMethod = invokeExpr.getMethod();
      if (baseType == null) {
        //If receiver's type cannot be resolved
        //Ignore this invocation site
        PsiCFGDebugUtil.LOG.warning("Receiver Type is null at " + invokeExpr.getSimpleName());
        return;
      }

      if (base instanceof ThisRef) {
        addInvokeExprWithThisRef(node, baseType, targetMethod);
      }
      else {
        performCHAForInvocationSite(node, baseType, targetMethod);
      }
    }
  }

  public void processSingleInvocationWithConstructorInvoke(GraphNode node, NewExpr newExpr) {
    PsiCFGMethod constructorMethod = newExpr.getConstructorInvocation();
    if (constructorMethod != null) {
      addToCallGraph(node, constructorMethod);
    }
    else {
      PsiCFGDebugUtil.LOG.warning("Constructor in New Expr is null: " + newExpr.getSimpleName());
    }
  }

  public Callgraph getCallGraph() {
    return this.mCallGraphInstance;
  }

}
