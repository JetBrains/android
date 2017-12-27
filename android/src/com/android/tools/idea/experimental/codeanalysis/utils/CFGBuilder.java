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
import com.android.tools.idea.experimental.codeanalysis.datastructs.PsiCFGClass;
import com.android.tools.idea.experimental.codeanalysis.datastructs.PsiCFGField;
import com.android.tools.idea.experimental.codeanalysis.datastructs.PsiCFGMethod;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.BlockGraph;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.Graph;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.MethodGraph;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.SwitchCaseGraph;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.impl.BlockGraphImpl;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.impl.MethodGraphImpl;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.impl.SwitchCaseGraphImpl;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.impl.SynchronizedBlockGraphImpl;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.*;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.impl.*;
import com.android.tools.idea.experimental.codeanalysis.datastructs.stmt.AssignStmt;
import com.android.tools.idea.experimental.codeanalysis.datastructs.stmt.Stmt;
import com.android.tools.idea.experimental.codeanalysis.datastructs.stmt.impl.AssignStmtImpl;
import com.android.tools.idea.experimental.codeanalysis.datastructs.stmt.impl.DeclarationStmtImpl;
import com.android.tools.idea.experimental.codeanalysis.datastructs.stmt.impl.ReturnStmtImpl;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.*;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.impl.*;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.psi.*;
import com.intellij.psi.impl.ResolveScopeManager;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Map;
import java.util.Stack;

/**
 * The CFG Builder for a single code block.
 */
public class CFGBuilder {

  protected PsiCFGScene mScene;
  protected BlockGraph mGraph;
  protected PsiCodeBlock psiCodeBlock;
  protected PsiCFGClass containerClass;
  protected PsiStatement[] mStatementList;

  protected ArrayList<GraphNode> returnNodeList;
  protected ArrayList<GraphNode> breakNodeList;

  private ArrayList<GraphNode> curWorkingNodeList;

  protected Stack<GraphNode> mNestedStack;

  protected Map<GraphNode, PsiIdentifier> mLabelMap;

  protected boolean mSwitchBlock = false;

  protected boolean mLambda = false;

  protected PsiExpression mLambdaBody;

  //protected Stack<> labeledStmtStack = null;

  /**
   * Constructor for builder of PsiCodeBlocks
   * E.g. for a non-abstract methods. Or a code block
   *
   * @param scene    Current Scene
   * @param bg       The initialized Block Graph.
   * @param cfgClass The Class that contains this code block
   * @param cb       The code block
   */
  public CFGBuilder(PsiCFGScene scene, BlockGraph bg, PsiCFGClass cfgClass, PsiCodeBlock cb) {
    this.mScene = scene;
    this.containerClass = cfgClass;
    this.mGraph = bg;
    this.psiCodeBlock = cb;
    this.mStatementList = null;
    this.curWorkingNodeList = Lists.newArrayList();
    this.mLambdaBody = null;
    if (bg instanceof MethodGraph) {
      mNestedStack = new Stack<>();
      mLabelMap = Maps.newHashMap();
    }
    else {
      mNestedStack = null;
      mLabelMap = null;
    }
  }

  /**
   * Constructor for builder of an array of statements
   *
   * @param scene         Current Scene
   * @param bg            The initialized Block Graph.
   * @param cfgClass      The Class that contains this code block
   * @param statementList The array of statements
   */
  public CFGBuilder(PsiCFGScene scene,
                    BlockGraph bg,
                    PsiCFGClass cfgClass,
                    PsiStatement[] statementList) {

    this.mScene = scene;
    this.containerClass = cfgClass;
    this.mGraph = bg;
    this.psiCodeBlock = null;
    this.mStatementList = statementList;
    this.curWorkingNodeList = Lists.newArrayList();
    this.mLambdaBody = null;
    if (bg instanceof MethodGraph) {
      mNestedStack = new Stack<>();
      mLabelMap = Maps.newHashMap();
    }
    else {
      mNestedStack = null;
      mLabelMap = null;
    }
  }

  /**
   * Constructor for builder of an lambda expression
   * The reason that we do not use the constructor that
   * accept an array of statements is that a lambda can only
   * have an expression instead of a statement. The value of
   * that expression will become the return value.
   *
   * @param scene      Current Scene
   * @param bg         The initialized Block Graph
   * @param cfgClass   The Class that contains this code block
   * @param lambdaBody The array of statements
   */
  public CFGBuilder(PsiCFGScene scene,
                    BlockGraph bg,
                    PsiCFGClass cfgClass,
                    PsiExpression lambdaBody) {

    this.mScene = scene;
    this.containerClass = cfgClass;
    this.mGraph = bg;
    this.psiCodeBlock = null;
    this.mStatementList = null;
    this.mLambdaBody = lambdaBody;
    this.curWorkingNodeList = Lists.newArrayList();
    if (bg instanceof MethodGraph) {
      mNestedStack = new Stack<>();
      mLabelMap = Maps.newHashMap();
    }
    else {
      mNestedStack = null;
      mLabelMap = null;
    }
  }

  /**
   * Put a flag so the builder knows it work on the lambda.
   */
  public void setLambdaFlag() {
    this.mLambda = true;
  }

  /**
   * Put a string tag in Block Entry and Exit node.
   * It will be output in the CFG's dot file. It does
   * not have any effect other than that.
   * @param graph The block graph that needs to be taged
   * @param tag The tag
   */
  private void setGraphEntryExitTag(@NotNull BlockGraph graph, @NotNull String tag) {
    GraphNode entry = graph.getEntryNode();
    GraphNode exit = graph.getExitNode();
    if (entry instanceof BlockGraphEntryNodeImpl) {
      ((BlockGraphEntryNodeImpl)entry).setTag(tag);
    }
    if (exit instanceof BlockGraphExitNodeImpl) {
      ((BlockGraphExitNodeImpl)exit).setTag(tag);
    }
  }

  /**
   * Used to handle nested loop with labeled breaks.
   * When constructing the block graph for the inner
   * loop, this method will pass in parent's loop stack.
   * @param loopStack The nested loop stack
   */
  public void setNestedStack(@NotNull Stack<GraphNode> loopStack) {
    this.mNestedStack = loopStack;
  }

  /**
   * The same, used to handle nested loop with labeled breaks.
   * @param loopLabelMap The loop label map.
   */
  public void setNestedLabelMap(Map<GraphNode, PsiIdentifier> loopLabelMap) {
    this.mLabelMap = loopLabelMap;
  }

  public void build() {

    if (mLambdaBody != null) {
      buildWSingleLambdaExpr();
    }
    else if (this.mGraph instanceof SwitchCaseGraph) {
      buildWSwitch();
    }
    else {
      buildWOSwitch();
    }
  }

  /**
   * Build the control flow graph for a single lambda expression   *
   */
  public void buildWSingleLambdaExpr() {
    curWorkingNodeList.clear();
    curWorkingNodeList.add(mGraph.getEntryNode());

    Value exprValue = dfsExpressionBuilder(mLambdaBody);
    //Create a return statement
    ReturnStmtImpl returnStmt = new ReturnStmtImpl(exprValue, null);
    GraphNodeImpl returnNode = new GraphNodeImpl(this.mGraph);
    returnNode.getStmtList().add(returnStmt);
    connectCurrentWorkingNode(returnNode);
    GraphNodeUtil.connectGraphNode(returnNode, mGraph.getExitNode());
  }

  /**
   * Build the CFG for the code block in the Switch Statement
   * As it has a completely different CFG.
   */
  public void buildWSwitch() {
    curWorkingNodeList.clear();
    PsiStatement[] firstLevelChildren = this.psiCodeBlock.getStatements();
    for (int i = 0; i < firstLevelChildren.length; i++) {
      PsiStatement statement = firstLevelChildren[i];
      if (statement instanceof PsiSwitchLabelStatement) {
        dfsPsiSwitchLabelStatementBuilder((PsiSwitchLabelStatement)statement);
      }
      else {
        if (isNonBranchingStatement(statement)) {
          buildNonBranchingStatements(statement);
        }
        else {
          buildBranchingStatements(statement);
        }
      }
    }
    GraphNode switchExit = this.mGraph.getExitNode();
    if (!curWorkingNodeList.isEmpty()) {
      for (GraphNode node : curWorkingNodeList) {
        if (!node.equals(switchExit)) {
          GraphNodeUtil.connectGraphNode(node, switchExit);
        }
      }
    }
  }

  /**
   * Build the CFG for a code block. The code block should not
   * belong to a switch statement.
   */
  public void buildWOSwitch() {

    //Stage 0
    //Iterate every child statement of codeblock
    PsiStatement[] firstLevelChildren = this.mStatementList;
    if (this.psiCodeBlock != null) {
      //Build the cfg without code block
      firstLevelChildren = this.psiCodeBlock.getStatements();
    }

    //Check whether the method body is completely empty.
    if (firstLevelChildren.length == 0) {
      //If no statement available, Connect the entry to the exit
      GraphNodeUtil.connectGraphNode(mGraph.getEntryNode(), mGraph.getExitNode());
      return;
    }

    curWorkingNodeList.clear();
    curWorkingNodeList.add(mGraph.getEntryNode());

    for (PsiStatement curPsiStmt : firstLevelChildren) {

      if (isWorkingNodeConsiderUnreachable(curWorkingNodeList)) {
        curWorkingNodeList.clear();
        curWorkingNodeList.add(this.mGraph.getUnreachableNodeEntry());
      }

      //Check if the statements can be sequencially build
      if (isNonBranchingStatement(curPsiStmt)) {
        buildNonBranchingStatements(curPsiStmt);
      }
      else {
        //It is a branching node
        buildBranchingStatements(curPsiStmt);
      }
    }

    GraphNode curExit = this.mGraph.getExitNode();
    for (GraphNode node : curWorkingNodeList) {
      if (!(node.equals(curExit))) {
        GraphNodeUtil.connectGraphNode(node, curExit);
      }
    }
    //Debug only
    //Output the CFG as dot file
    //if (this.mGraph instanceof MethodGraph) {
    //  MethodGraph mg = (MethodGraph)this.mGraph;
    //  CFGUtil.outputCFGDotFile(mg);
    //}
  }

  /**
   * In some cases, the code is not reachable based on the control flow.
   * For example, the statements after the return statement.
   * In this case, the workingNodes will be empty or only contain null.
   * @param workingNodes The current working node
   * @return If working node consider unreachable.
   */
  public boolean isWorkingNodeConsiderUnreachable(ArrayList<GraphNode> workingNodes) {
    boolean retVal = true;
    for (GraphNode curNode : workingNodes) {
      if (curNode != null) {
        return false;
      }
    }
    return retVal;
  }

  /**
   * Build the node for the statement that will not cause a jump
   * For example, the declaration, math expressions etc.
   * @param statement The statement that will not cause a jump.
   */
  public void buildNonBranchingStatements(PsiStatement statement) {

    if (statement instanceof PsiDeclarationStatement) {
      dfsDeclarationStatementBuilder((PsiDeclarationStatement)statement);
    }
    else if (statement instanceof PsiExpressionStatement) {
      dfsExpressionStatementBuilder((PsiExpressionStatement)statement);
    }
    else if (statement instanceof PsiExpressionListStatement) {
      dfsExpressionListStatementBuilder((PsiExpressionListStatement)statement);
    }
    else if (statement instanceof PsiEmptyStatement) {
      //EmptyStatement can be skipped.
    }
    else {
      PsiCFGDebugUtil.LOG.warning("Not Implemented for statement " + statement.toString());
    }
  }

  /**
   * Resolve the parameter at the current context.
   * @param param The parameter
   * @return The PsiCFG wrapper for the parameter
   */
  @Nullable
  private Param resolveParam(@NotNull PsiParameter param) {
    Graph graph = this.mGraph;
    if (graph instanceof BlockGraph) {
      Param p = ((BlockGraph)graph).getParamFromPsiParameter(param);
      if (p == null) {
        if (graph instanceof MethodGraph) {
          MethodGraph methodGraph = (MethodGraph) graph;
          PsiCFGDebugUtil.LOG.warning("Parameter " + param.getName() + " does not exist in method "
                                          + methodGraph.getPsiCFGMethod().getName() + " in class "
                                          + this.containerClass.getQualifiedClassName());
        } else {
          PsiStatement psiStmt = ((BlockGraph)graph).getParentStmt();
          PsiCFGDebugUtil.LOG.warning("Parameter " + param.getName() + " does not exist in context "
                                      + psiStmt.getText() + " in class "
                                      + this.containerClass.getQualifiedClassName());
        }
      }
      return p;
    }
    PsiCFGDebugUtil.LOG.warning("Parameter " + param.getName() + " does not exist, the graph is not" +
                                " a block graph");
    return null;
  }

  /**
   * Resolve the local variable at the current context
   * @param localVar The local
   * @return The PsiCFG wrapper for the local.
   */
  @Nullable
  private Local resolveLocal(@NotNull PsiLocalVariable localVar) {
    Local l = this.mGraph.getLocalFromPsiLocal(localVar);
    if (l != null) {
      return l;
    }

    if (this.containerClass.isNested()) {
      //Nested class
      BlockGraph parentBlock = this.containerClass.getDeclaringBlock();
      l = parentBlock.getLocalFromPsiLocal(localVar);
    }

    if (l == null) {
      PsiCFGDebugUtil.LOG.warning(String.format("Local %s cannot be resolved in Class %s",
                                                localVar.getText(),
                                                this.containerClass.getQualifiedClassName()
                                             ));
    }

    return l;
  }

  /**
   * Return a PsiType object by the given PsiClass
   * @param clazz class that should put into the PsiType
   * @return The constructed type
   */
  @NotNull
  private PsiType retrieveTypeByPsiClass(@NotNull PsiClass clazz) {
    PsiElementFactory factory = JavaPsiFacade.getInstance(mScene.getProject()).getElementFactory();
    PsiType retType = factory.createType(clazz);
    return retType;
  }

  /**
   * Retrieve the method instance of this block graph.
   * @return The method instance
   */
  @NotNull
  private PsiCFGMethod retrieveDeclaringMethod() {
    Graph graph = this.mGraph;
    while (graph != null && (!(graph instanceof MethodGraphImpl))) {
      graph = graph.getParentGraph();
    }
    if (graph == null) {
      throw new RuntimeException("Cannot find declaring method: ");
    } else {
      MethodGraphImpl methodGraph = (MethodGraphImpl) graph;
      return methodGraph.getPsiCFGMethod();
    }
  }

  private boolean isNonBranchingStatement(PsiStatement statement) {
    if (statement instanceof PsiDeclarationStatement) {
      return true;
    }
    else if (statement instanceof PsiExpressionStatement) {
      return true;
    }
    else if (statement instanceof PsiExpressionListStatement) {
      return true;
    }
    else if (statement instanceof PsiEmptyStatement) {
      return true;
    }

    return false;
  }

  /**
   * It is a little bit tricky to handle the labeled statement
   * in Java. Unlike C/C++, java does not have have goto statement,
   * even though it is a reserved keyword. The label can only used in
   * (break/continue) statements. The continue with label can only be
   * used in loops. However, the break is something different. You can
   * use break jump out of if statement. Or in a block statement.
   * E.g.
   * outerIf:if (booleanExpr) {
   * ...
   * if (anotherBooleanExpr) {
   * break:outerIf;
   * }
   * }
   * is legit.
   *
   * outerBlock:{
   * SomeStmts
   * break outerBlock;
   * }
   *
   * uselessLabel:break uselessLabel;
   *
   * are both legit.
   *
   * ---For the break label statement. Current solution is at the end of
   * this builder, put all breaks node into the curWorkingList. they will
   * be connected automatically to their target node by other builders
   * For the continue label;---
   * ---For current design, unless the user use label:break label; The labeled
   * statement will always followed by a new BlockGraph.-- (This is not going
   * work if the user use if else statement)
   *
   * So the current solution is, ignore any Labeled stmt that is not in a loop.
   * The CFG may lose 1 edge, but will not cause any reachability issue.
   *
   * The getStatement() should return a loop,
   * The continue label can be connected to the entry of the condition
   * check statement
   *
   * @param statement
   */
  public void dfsPsiLabeledStatementBuilder(PsiLabeledStatement statement) {
    PsiStatement labeledStatement = statement.getStatement();
    PsiIdentifier label = statement.getLabelIdentifier();

    if (labeledStatement instanceof PsiBreakStatement) {
      //A useless statement. label: break label;
      //Why people will want to write this.
      return;
    }

    if (labeledStatement instanceof PsiLoopStatement) {
      //Do something with the loop
      dfsPsiLoopStatementBuilder((PsiLoopStatement)labeledStatement, label);
    }
    //Other statements
    //Just assume the lable does not exist
    if (isNonBranchingStatement(labeledStatement)) {
      buildNonBranchingStatements(labeledStatement);
    }
    else {
      //It is a branching node
      buildBranchingStatements(labeledStatement);
    }
  }

  /**
   * Build nodes for case "label" statement.
   * @param statement
   */
  public void dfsPsiSwitchLabelStatementBuilder(PsiSwitchLabelStatement statement) {
    PsiExpression caseValueExpr = statement.getCaseValue();
    if (!(this.mGraph instanceof SwitchCaseGraph)) {
      //Not a inside a switch but encountered case?
      PsiCFGDebugUtil.LOG.warning("Case statement out side of switch in :");
      PsiCFGDebugUtil.debugOutputPsiElement(statement);
      return;
    }
    SwitchCaseGraphImpl switchGraph = (SwitchCaseGraphImpl)this.mGraph;
    SwitchBranchingNodeImpl switchNode =
      (SwitchBranchingNodeImpl)switchGraph.getSwitchBranchingNode();

    //Create a case node for this statement
    CaseNodeImpl curCaseNode = new CaseNodeImpl(this.mGraph);
    Value caseValue;
    //Evaluate the constant
    if (statement.isDefaultCase()) {
      //It is a default case
      curCaseNode.setDefault();
      switchNode.setDefaultTarget(curCaseNode);

    } else {
      //A "case value: " statement
      if (caseValueExpr == null) {
        PsiCFGDebugUtil.LOG.warning("CaseExpr is null in " + statement.getText());
        return;
      }
      if (caseValueExpr instanceof PsiLiteralExpression) {
        caseValue = dfsLiteralExpressionBuilder((PsiLiteralExpression)caseValueExpr);
      }
      else if (caseValueExpr instanceof PsiReferenceExpression) {
        //TODO: Add more sophisticate check for Reference Expression
        //Only primitive types are allowed in switch case.
        caseValue = dfsRHSReferenceExpressionBuilder((PsiReferenceExpression)caseValueExpr);
      }
      else {
        PsiCFGDebugUtil.LOG.warning("Case is not using a constant or reference");
        PsiCFGDebugUtil.debugOutputPsiElement(caseValueExpr);
        caseValue = new DummyRef(caseValueExpr.getType(), caseValueExpr);
      }
      curCaseNode.setLabelValue(caseValue);
      switchNode.setTargetViaKey(caseValue, curCaseNode);
    }

    switchGraph.addCase(curCaseNode);

    if (!curWorkingNodeList.isEmpty()) {
      connectCurrentWorkingNode(curCaseNode);
    }
    curWorkingNodeList.clear();
    curWorkingNodeList.add(curCaseNode);
  }

  /**
   * If the return statement does not exist, the if statement should always return 2 nodes
   * In the if else branch
   *
   * @param statement
   */
  public void dfsPsiIfStatementBuilder(PsiIfStatement statement) {

    PsiExpression conditionExpression = statement.getCondition();
    PsiStatement psiThenStmt = statement.getThenBranch();
    PsiStatement psiElseStmt = statement.getElseBranch();

    //Evaluate the condition expression
    assert conditionExpression != null;
    Value conditionExprLocal = dfsExpressionBuilder(conditionExpression);
    //IfBranchingNodeImpl ifNode = new IfBranchingNodeImpl(this.mGraph, conditionExprLocal,)


    assert psiThenStmt != null;
    //Build the then branch
    BlockGraphImpl thenBranchGraph = new BlockGraphImpl();

    setGraphEntryExitTag(thenBranchGraph, "[IF THEN]");
    thenBranchGraph.setParentGraph(this.mGraph);
    thenBranchGraph.setParentStmt(statement);
    CFGBuilder thenBranchBuilder;
    if (psiThenStmt instanceof PsiBlockStatement) {
      //It is a code block
      PsiCodeBlock psiThenBlock = ((PsiBlockStatement)psiThenStmt).getCodeBlock();
      thenBranchBuilder =
        new CFGBuilder(this.mScene, thenBranchGraph, this.containerClass, psiThenBlock);

    }
    else {
      //It is not a code block
      PsiStatement[] thenBranchStmtArray = new PsiStatement[1];
      thenBranchStmtArray[0] = psiThenStmt;
      thenBranchBuilder =
        new CFGBuilder(this.mScene, thenBranchGraph, this.containerClass, thenBranchStmtArray);

    }
    thenBranchBuilder.setNestedLabelMap(mLabelMap);
    thenBranchBuilder.setNestedStack(mNestedStack);
    thenBranchBuilder.build();

    //Build the else branch if exist
    BlockGraphImpl elseBranchGraph = null;
    if (psiElseStmt != null) {
      elseBranchGraph = new BlockGraphImpl();
      setGraphEntryExitTag(elseBranchGraph, "[IF ELSE]");
      elseBranchGraph.setParentGraph(this.mGraph);
      elseBranchGraph.setParentStmt(statement);
      CFGBuilder elseBranchBuilder;
      if (psiElseStmt instanceof PsiBlockStatement) {
        PsiCodeBlock psiElseBlock = ((PsiBlockStatement)psiElseStmt).getCodeBlock();
        elseBranchBuilder =
          new CFGBuilder(this.mScene, elseBranchGraph, this.containerClass, psiElseBlock);

      }
      else {
        PsiStatement[] elseBranchStmtArray = new PsiStatement[1];
        elseBranchStmtArray[0] = psiElseStmt;
        elseBranchBuilder =
          new CFGBuilder(this.mScene, elseBranchGraph, this.containerClass, elseBranchStmtArray);
      }
      elseBranchBuilder.setNestedLabelMap(mLabelMap);
      elseBranchBuilder.setNestedStack(mNestedStack);
      elseBranchBuilder.build();
    }

    //Both then and else branch are ready
    IfBranchingNodeImpl ifNode =
      new IfBranchingNodeImpl(this.mGraph, conditionExprLocal, thenBranchGraph, elseBranchGraph);

    connectCurrentWorkingNode(ifNode);
    curWorkingNodeList.clear();

    if (thenBranchGraph.getExitNode().getIn().length > 0) {
      curWorkingNodeList.add(thenBranchGraph.getExitNode());
    }
    if (elseBranchGraph != null) {
      if (elseBranchGraph.getExitNode().getIn().length > 0) {
        curWorkingNodeList.add(elseBranchGraph.getExitNode());
      }
    }
    else {
      curWorkingNodeList.add(ifNode.getFalseBranch());
    }
  }

  private void pushLoopNode(LoopBranchingNodeImpl loopNode, PsiIdentifier id) {
    this.mNestedStack.push(loopNode);
    this.mLabelMap.put(loopNode, id);
  }

  private void popLoopNode() {
    GraphNode topNode = mNestedStack.pop();
    mLabelMap.remove(topNode);
  }

  /**
   * Build nodes for "for" loop.
   * @param statement The PsiForStatement
   * @param label The identifier of the label, can be null.
   */
  public void dfsPsiForStatementBuilder(@NotNull PsiForStatement statement,
                                        @Nullable PsiIdentifier label) {

    PsiStatement loopBody = statement.getBody();
    int loopType;
    LoopBranchingNodeImpl loopNode;
    GraphNode conditionCheckEntry;
    ConditionCheckNode conditionCheckExit;
    GraphNode postLoopEntry;
    GraphNode postLoopExit;

    loopType = LoopBranchingNode.FOR_LOOP;
    //Evaluate the init code
    PsiForStatement forStmt = statement;
    PsiStatement initStmt = forStmt.getInitialization();
    if (initStmt != null) {
      buildNonBranchingStatements(initStmt);
    }

    loopNode = new LoopBranchingNodeImpl(this.mGraph, loopType);
    connectCurrentWorkingNode(loopNode);

    //Push this loopNode into the stack
    pushLoopNode(loopNode, label);

    //Eval the condition check
    PsiExpression psiConditionCheckCode = forStmt.getCondition();
    Value finalCheckVal = dfsExpressionBuilder(psiConditionCheckCode);

    //Build the final condition check for this loop
    conditionCheckExit = new ConditionCheckNodeImpl(this.mGraph, finalCheckVal);

    if (loopNode.getOut().length == 0) {
      //The dummy loop node does not have any out edges
      //In this case the conditionCheckEntry is the same
      //as the conditionCheckExit
      conditionCheckEntry = conditionCheckExit;
    }
    else {
      //At this point the dummy LoopNode is connected to the entry
      //of the condition check code.
      conditionCheckEntry = loopNode.getOut()[0];
    }

    connectCurrentWorkingNode(conditionCheckExit);
    //Build the loop body
    BlockGraph loopBodyGraph = loopbodyBuilder(loopBody, statement);
    //Connect the condition check code true branch to loop entry
    GraphNodeUtil.connectGraphNode(conditionCheckExit.getTrueBranch(),
                                   loopBodyGraph.getEntryNode());

    curWorkingNodeList.clear();
    curWorkingNodeList.add(loopBodyGraph.getExitNode());

    //Eval Post loop code
    PsiStatement postLoopUpdateStatment = forStmt.getUpdate();
    buildNonBranchingStatements(postLoopUpdateStatment);
    postLoopEntry = loopBodyGraph.getExitNode().getOut()[0];
    postLoopExit = curWorkingNodeList.get(0);

    //Connect the post loop to the condition check entry
    connectCurrentWorkingNode(conditionCheckEntry);
    curWorkingNodeList.clear();
    curWorkingNodeList.add(conditionCheckExit.getFalseBranch());

    loopNode.setConditionCheckEntry(conditionCheckEntry);
    loopNode.setConditionCheckExitNode(conditionCheckExit);
    loopNode.setPostLoopEntryNode(postLoopEntry);
    loopNode.setPostLoopExitNode(postLoopExit);
    loopNode.setLoopBody(loopBodyGraph);
    //For loop build complete

    //Process the break and continue nodes
    loopNode.connectSpecialNodes();
    popLoopNode();
  }

  /**
   * Build nodes for "while" loops
   * @param statement The while statement
   * @param label The label for this loop. In case of labeled break.
   */
  public void dfsPsiWhileStatementBuilder(@NotNull PsiWhileStatement statement,
                                          @Nullable PsiIdentifier label) {
    PsiStatement loopBody = statement.getBody();
    int loopType;
    LoopBranchingNodeImpl loopNode;
    GraphNode conditionCheckEntry;
    ConditionCheckNode conditionCheckExit;
    GraphNode postLoopEntry;
    GraphNode postLoopExit;

    PsiWhileStatement whileStmt = (PsiWhileStatement)statement;
    loopType = LoopBranchingNode.WHILE_LOOP;
    loopNode = new LoopBranchingNodeImpl(this.mGraph, loopType);
    connectCurrentWorkingNode(loopNode);

    //Push this loopNode into the stack
    pushLoopNode(loopNode, label);

    //Eval the condition check
    PsiExpression psiConditionCheckCode = whileStmt.getCondition();
    Value finalCheckVal = dfsExpressionBuilder(psiConditionCheckCode);

    //Build the final condition check for this loop
    conditionCheckExit = new ConditionCheckNodeImpl(this.mGraph, finalCheckVal);

    if (loopNode.getOut().length == 0) {
      //The dummy loop node does not have any out edges
      //In this case the conditionCheckEntry is the same
      //as the conditionCheckExit
      conditionCheckEntry = conditionCheckExit;
    }
    else {
      //At this point the dummy LoopNode is connected to the entry
      //of the condition check code.
      conditionCheckEntry = loopNode.getOut()[0];
    }
    connectCurrentWorkingNode(conditionCheckExit);
    //Build the loop body
    BlockGraph loopBodyGraph = loopbodyBuilder(loopBody, statement);
    //Connect the condition check code true branch to loop entry
    GraphNodeUtil.connectGraphNode(conditionCheckExit.getTrueBranch(),
                                   loopBodyGraph.getEntryNode());

    curWorkingNodeList.clear();
    curWorkingNodeList.add(loopBodyGraph.getExitNode());

    //Connect the loopbody exit to the condition check entry
    connectCurrentWorkingNode(conditionCheckEntry);
    curWorkingNodeList.clear();
    curWorkingNodeList.add(conditionCheckExit.getFalseBranch());

    loopNode.setConditionCheckEntry(conditionCheckEntry);
    loopNode.setConditionCheckExitNode(conditionCheckExit);
    loopNode.setPostLoopEntryNode(null);
    loopNode.setPostLoopExitNode(null);
    loopNode.setLoopBody(loopBodyGraph);
    //While loop build complete
    //Process the break and continue nodes
    loopNode.connectSpecialNodes();
    popLoopNode();
  }

  /**
   * Build nodes for "Do While" loops
   * @param statement The while statement
   * @param label The label for this loop. In case of labeled break.
   */
  public void dfsPsiDoWhileStatementBuilder(@NotNull PsiDoWhileStatement statement,
                                            @Nullable PsiIdentifier label) {

    PsiStatement loopBody = statement.getBody();
    int loopType;
    LoopBranchingNodeImpl loopNode;
    GraphNode conditionCheckEntry;
    ConditionCheckNode conditionCheckExit;
    GraphNode postLoopEntry;
    GraphNode postLoopExit;

    loopType = LoopBranchingNode.DOWHILE_LOOP;
    PsiDoWhileStatement dowhileStmt = (PsiDoWhileStatement)statement;
    loopNode = new LoopBranchingNodeImpl(this.mGraph, loopType);
    connectCurrentWorkingNode(loopNode);

    //Push this loopNode into the stack
    pushLoopNode(loopNode, label);

    //Eval the loopbody
    BlockGraph loopBodyGraph = loopbodyBuilder(loopBody, statement);
    connectCurrentWorkingNode(loopBodyGraph.getEntryNode());
    curWorkingNodeList.clear();
    curWorkingNodeList.add(loopBodyGraph.getExitNode());
    //Eval the condition check
    PsiExpression psiConditionCheckCode = dowhileStmt.getCondition();
    Value finalCheckVal = dfsExpressionBuilder(psiConditionCheckCode);
    conditionCheckExit = new ConditionCheckNodeImpl(this.mGraph, finalCheckVal);

    if (loopBodyGraph.getExitNode().getOut().length == 0) {
      //The dummy loop node does not have any out edges
      //In this case the conditionCheckEntry is the same
      //as the conditionCheckExit
      conditionCheckEntry = conditionCheckExit;
    }
    else {
      //At this point the dummy LoopNode is connected to the entry
      //of the condition check code.
      conditionCheckEntry = loopBodyGraph.getExitNode().getOut()[0];
    }

    connectCurrentWorkingNode(conditionCheckExit);
    //True branch connect back to the body
    GraphNodeUtil.connectGraphNode(conditionCheckExit.getTrueBranch(),
                                   loopBodyGraph.getEntryNode());

    curWorkingNodeList.clear();
    curWorkingNodeList.add(conditionCheckExit.getFalseBranch());

    loopNode.setConditionCheckEntry(conditionCheckEntry);
    loopNode.setConditionCheckExitNode(conditionCheckExit);
    loopNode.setLoopBody(loopBodyGraph);
    loopNode.setPostLoopEntryNode(null);
    loopNode.setPostLoopExitNode(null);
    //Do while loop build complete;
    //Process the break and continue nodes
    loopNode.connectSpecialNodes();
    popLoopNode();

  }

  /**
   * Build nodes for "for each" loops
   * @param statement The foreach statement
   * @param label The label for this loop. In case of labeled break.
   */
  public void dfsPsiForeachStatementBuilder(@NotNull PsiForeachStatement statement,
                                            @Nullable PsiIdentifier label) {

    PsiStatement loopBody = statement.getBody();
    int loopType;
    LoopBranchingNodeImpl loopNode;
    GraphNode conditionCheckEntry;
    ConditionCheckNode conditionCheckExit;
    GraphNode postLoopEntry;
    GraphNode postLoopExit;

    loopType = LoopBranchingNode.FOREACH_LOOP;

    //for (Type iter : val);
    //The iter is the iterParam here
    //The val is the iterValue here.
    PsiParameter iterParam = statement.getIterationParameter();
    PsiExpression iterValue = statement.getIteratedValue();

    if (iterParam == null) {
      //TODO: Log it and return
      return;
    }

    if (iterParam == null) {
      //TODO: Log it and return
      return;
    }

    Value iterV = dfsExpressionBuilder(iterValue);
    Param iterP = new ParamImpl(iterParam);
    loopNode = new LoopBranchingNodeImpl(this.mGraph, loopType);
    connectCurrentWorkingNode(loopNode);

    pushLoopNode(loopNode, label);

    BlockGraph loopBodyGraph = loopbodyBuilder(loopBody, statement, iterParam, iterP);
    loopNode.setLoopBody(loopBodyGraph);
    loopNode.setForeachIteratorParam(iterP);
    loopNode.setForeachIteratorValue(iterV);

    GraphNodeUtil.connectGraphNode(loopNode, loopBodyGraph.getEntryNode());
    GraphNodeUtil.connectGraphNode(loopNode, loopBodyGraph.getExitNode());
    GraphNodeUtil.connectGraphNode(loopBodyGraph.getExitNode(), loopNode);
    loopNode.connectSpecialNodes();

    curWorkingNodeList.clear();
    curWorkingNodeList.add(loopBodyGraph.getExitNode());

    popLoopNode();

  }

  /**
   * Build nodes for loop statement.
   * The For loop, while loop and do while loop
   * @param statement The loop statement
   * @param label The label of loop. Can be null
   */
  public void dfsPsiLoopStatementBuilder(@NotNull PsiLoopStatement statement,
                                         @Nullable PsiIdentifier label) {
    PsiStatement loopBody = statement.getBody();
    int loopType;
    LoopBranchingNodeImpl loopNode;
    GraphNode conditionCheckEntry;
    ConditionCheckNode conditionCheckExit;
    GraphNode postLoopEntry;
    GraphNode postLoopExit;

    if (statement instanceof PsiForStatement) {
      dfsPsiForStatementBuilder((PsiForStatement) statement, label);
    }
    else if (statement instanceof PsiWhileStatement) {
      dfsPsiWhileStatementBuilder((PsiWhileStatement) statement, label);
    }
    else if (statement instanceof PsiDoWhileStatement) {
      dfsPsiDoWhileStatementBuilder((PsiDoWhileStatement)statement, label);
    }
    else if (statement instanceof PsiForeachStatement){
      dfsPsiForeachStatementBuilder((PsiForeachStatement) statement, label);
    }
    else {
      //Some loop that have never seen?
      //ignore it but log it
      PsiCFGDebugUtil.LOG.warning("Unknown loop:" + statement.getClass().getSimpleName());
    }
  }

  /**
   * Build CFG for loop body for for/while/dowhile loops
   * @param statement The loop body
   * @param parentStatment The loop statement
   * @return The constructed CFG
   */
  private BlockGraph loopbodyBuilder(PsiStatement statement, PsiStatement parentStatment) {
    BlockGraphImpl loopBody = new BlockGraphImpl();
    loopBody.setParentGraph(this.mGraph);
    loopBody.setParentStmt(parentStatment);
    CFGBuilder builder;

    if (statement instanceof PsiBlockStatement) {
      PsiCodeBlock block = ((PsiBlockStatement)statement).getCodeBlock();
      builder = new CFGBuilder(this.mScene, loopBody, this.containerClass, block);
    }
    else {
      PsiStatement[] loopStmtArray = new PsiStatement[1];
      loopStmtArray[0] = statement;
      builder = new CFGBuilder(this.mScene, loopBody, this.containerClass, loopStmtArray);
    }
    builder.setNestedLabelMap(mLabelMap);
    builder.setNestedStack(mNestedStack);
    builder.build();
    setGraphEntryExitTag(loopBody, "[LOOP]");
    return loopBody;
  }

  /**
   * Build CFG for loop body foreach loops
   * @param statement The loop body
   * @param parentStatment The loop statements
   * @param psiParam The parameter in foreach loop
   * @param param The wrapped param in the foreach loop
   * @return The constructed CFG
   */
  private BlockGraph loopbodyBuilder(PsiStatement statement,
                                     PsiStatement parentStatment,
                                     PsiParameter psiParam,
                                     Param param) {
    BlockGraphImpl loopBody = new BlockGraphImpl();
    loopBody.addParam(psiParam, param);
    loopBody.setParentGraph(this.mGraph);
    loopBody.setParentStmt(parentStatment);
    CFGBuilder builder;

    if (statement instanceof PsiBlockStatement) {
      PsiCodeBlock block = ((PsiBlockStatement)statement).getCodeBlock();
      builder = new CFGBuilder(this.mScene, loopBody, this.containerClass, block);
    }
    else {
      PsiStatement[] loopStmtArray = new PsiStatement[1];
      loopStmtArray[0] = statement;
      builder = new CFGBuilder(this.mScene, loopBody, this.containerClass, loopStmtArray);
    }
    builder.setNestedLabelMap(mLabelMap);
    builder.setNestedStack(mNestedStack);
    builder.build();
    setGraphEntryExitTag(loopBody, "[LOOP]");
    return loopBody;
  }

  /**
   * Build the nodes for the block statement.
   * {
   *
   * }
   * @param statement The block statement
   */
  public void dfsPsiBlockStatementBuilder(PsiBlockStatement statement) {
    PsiCodeBlock codeBlock = statement.getCodeBlock();
    BlockGraphImpl blockStatementGraph = new BlockGraphImpl();
    blockStatementGraph.setParentGraph(this.mGraph);
    blockStatementGraph.setParentStmt(statement);
    CFGBuilder builder =
      new CFGBuilder(this.mScene, blockStatementGraph, this.containerClass, codeBlock);

    builder.setNestedLabelMap(mLabelMap);
    builder.setNestedStack(mNestedStack);
    builder.build();
    connectCurrentWorkingNode(blockStatementGraph.getEntryNode());
    curWorkingNodeList.clear();
    //TODO: What if the exit node unreachable?
    curWorkingNodeList.add(blockStatementGraph.getExitNode());
  }

  /**
   * Build the nodes for the synchronized block statement.
   * synchronized {
   *
   * }
   * @param statement The synchronized statement
   */
  public void dfsPsiSynchronizedStatementBuilder(PsiSynchronizedStatement statement) {
    PsiExpression syncExpr = statement.getLockExpression();
    Value syncExprVal = dfsExpressionBuilder(syncExpr);
    PsiCodeBlock codeBlock = statement.getBody();

    SynchronizedBlockGraphImpl blockStatementGraph = new SynchronizedBlockGraphImpl();
    blockStatementGraph.setParentGraph(this.mGraph);
    blockStatementGraph.setParentStmt(statement);
    blockStatementGraph.setSynchronizedExpression(syncExprVal);
    CFGBuilder builder =
      new CFGBuilder(this.mScene, blockStatementGraph, this.containerClass, codeBlock);

    builder.setNestedLabelMap(mLabelMap);
    builder.setNestedStack(mNestedStack);
    builder.build();
    connectCurrentWorkingNode(blockStatementGraph.getEntryNode());
    curWorkingNodeList.clear();
    //TODO: What if the exit node unreachable?
    curWorkingNodeList.add(blockStatementGraph.getExitNode());
  }

  /**
   * Build the nodes for the switch statement
   * switch (value) {
   *
   * }
   * @param statement The switch statement
   */
  public void dfsPsiSwitchStatementBuilder(PsiSwitchStatement statement) {
    PsiExpression checkedPsiExpr = statement.getExpression();
    PsiCodeBlock switchCodeBlock = statement.getBody();
    Value checkedValue = dfsExpressionBuilder(checkedPsiExpr);
    SwitchBranchingNodeImpl switchNode = new SwitchBranchingNodeImpl(this.mGraph);
    switchNode.setCheckedValue(checkedValue);
    this.mNestedStack.push(switchNode);
    //TODO: Support switch statement with label
    this.mLabelMap.put(switchNode, null);

    //Build the body for the switch Statement
    SwitchCaseGraphImpl switchGraph = new SwitchCaseGraphImpl();
    switchGraph.setParentGraph(this.mGraph);
    switchNode.setSwitchCaseGraph(switchGraph);
    switchGraph.setSwitchBranchingNode(switchNode);

    CFGBuilder switchGraphBuilder =
      new CFGBuilder(this.mScene, switchGraph, this.containerClass, switchCodeBlock);

    switchGraphBuilder.setNestedStack(this.mNestedStack);
    switchGraphBuilder.setNestedLabelMap(this.mLabelMap);
    switchGraphBuilder.build();
    connectCurrentWorkingNode(switchNode);
    curWorkingNodeList.clear();
    curWorkingNodeList.add(switchNode.getSwitchCaseGraph().getExitNode());
    //SwitchStatement construction finished.
    //Restore the stack
    this.mLabelMap.remove(switchNode);
    this.mNestedStack.pop();
  }

  /**
   * Build the nodes for the statements that will cause a jump
   * e.g. if/break/continue
   * @param statement
   */
  public void buildBranchingStatements(PsiStatement statement) {
    ArrayList<GraphNodeImpl> retList = Lists.newArrayList();
    if (statement instanceof PsiIfStatement) {
      dfsPsiIfStatementBuilder((PsiIfStatement)statement);
      return;
    }
    else if (statement instanceof PsiSynchronizedStatement) {
      dfsPsiSynchronizedStatementBuilder((PsiSynchronizedStatement)statement);
      return;
    }
    else if (statement instanceof PsiBreakStatement) {
      dfsPsiBreakStatementBuilder((PsiBreakStatement)statement);
      return;
    }
    else if (statement instanceof PsiReturnStatement) {
      dfsPsiReturnStmtBuilder((PsiReturnStatement)statement);
      return;
    }
    else if (statement instanceof PsiSwitchStatement) {
      dfsPsiSwitchStatementBuilder((PsiSwitchStatement)statement);
      return;
    }
    else if (statement instanceof PsiSwitchLabelStatement) {
      dfsPsiSwitchLabelStatementBuilder((PsiSwitchLabelStatement)statement);
      return;
    }
    else if (statement instanceof PsiLoopStatement) {
      dfsPsiLoopStatementBuilder((PsiLoopStatement)statement, null);
      return;
    }
    else if (statement instanceof PsiThrowStatement) {
      //TODO
    }
    else if (statement instanceof PsiAssertStatement) {
      //TODO
    }
    else if (statement instanceof PsiContinueStatement) {
      //Return LoopEntry Node
      dfsPsiContinueStatementBuilder((PsiContinueStatement)statement);
      return;
    }
    else if (statement instanceof PsiTryStatement) {
      dfsPsiTryStatementBuilder((PsiTryStatement) statement);
    }
    else if (statement instanceof PsiBlockStatement) {
      dfsPsiBlockStatementBuilder((PsiBlockStatement)statement);
    }
    else if (statement instanceof PsiLabeledStatement) {
      dfsPsiLabeledStatementBuilder((PsiLabeledStatement)statement);
    }
  }

  /**
   * Create node for try catch nodes, a temp workaround
   * @param statement The try statement.
   */
  public void dfsPsiTryStatementBuilder(PsiTryStatement statement) {
    PsiCodeBlock psiTryBlock = statement.getTryBlock();
    PsiCodeBlock psiFinallyBlock = statement.getFinallyBlock();

    if (psiTryBlock != null) {
      BlockGraphImpl tryBlockStatementGraph = new BlockGraphImpl();
      tryBlockStatementGraph.setParentGraph(this.mGraph);
      tryBlockStatementGraph.setParentStmt(statement);
      CFGBuilder tryBuilder =
        new CFGBuilder(this.mScene, tryBlockStatementGraph, this.containerClass, psiTryBlock);

      tryBuilder.setNestedLabelMap(mLabelMap);
      tryBuilder.setNestedStack(mNestedStack);
      tryBuilder.build();
      connectCurrentWorkingNode(tryBlockStatementGraph.getEntryNode());
      curWorkingNodeList.clear();
      //TODO: What if the exit node unreachable?
      curWorkingNodeList.add(tryBlockStatementGraph.getExitNode());
    }

    if (psiFinallyBlock != null) {
      BlockGraphImpl finallyBlockStatementGraph = new BlockGraphImpl();
      finallyBlockStatementGraph.setParentGraph(this.mGraph);
      finallyBlockStatementGraph.setParentStmt(statement);
      CFGBuilder finallyBuilder =
        new CFGBuilder(this.mScene, finallyBlockStatementGraph, this.containerClass,
                       psiFinallyBlock);

      finallyBuilder.setNestedLabelMap(mLabelMap);
      finallyBuilder.setNestedStack(mNestedStack);
      finallyBuilder.build();
      connectCurrentWorkingNode(finallyBlockStatementGraph.getEntryNode());
      curWorkingNodeList.clear();
      //TODO: What if the exit node unreachable?
      curWorkingNodeList.add(finallyBlockStatementGraph.getExitNode());
    }
  }

  /**
   * From top to the bottom of the stack, look for the loopNode that
   * has correct identifier
   *
   * @param id
   * @return The found loopNode. Otherwise return null
   */
  protected LoopBranchingNodeImpl lookupLoopNodeFromIdentifier(PsiIdentifier id) {
    for (int i = mNestedStack.size() - 1; i >= 0; i--) {
      GraphNode curNode = mNestedStack.get(i);
      if (!(curNode instanceof LoopBranchingNodeImpl)) {
        continue;
      }

      PsiIdentifier idForCurNode = mLabelMap.get(curNode);
      if (idForCurNode != null && idForCurNode.getText().equals(id.getText())) {
        return (LoopBranchingNodeImpl)curNode;
      }
    }
    return null;
  }

  protected LoopBranchingNodeImpl lookForTopMostLoop() {
    for (int i = mNestedStack.size() - 1; i >= 0; i--) {
      GraphNode curNode = mNestedStack.get(i);
      if (curNode instanceof LoopBranchingNodeImpl) {
        return (LoopBranchingNodeImpl)curNode;
      }
    }
    return null;
  }

  /**
   * Build the nodes for the break and labeled break.
   * The strategy is to find the target loop node and add this
   * break node to the loop node. After the construction of the
   * loop node is complete, the break node will be connected to
   * the correct target.
   * @param statement The break statement.
   */
  public void dfsPsiBreakStatementBuilder(PsiBreakStatement statement) {
    //Not in a loop. quit
    if (mNestedStack.isEmpty()) {
      return;
    }
    PsiIdentifier label = statement.getLabelIdentifier();
    if (label == null) {
      //Unlabeled break. Only available in loops
      GraphNode curTop = mNestedStack.peek();
      if (curTop instanceof LoopBranchingNodeImpl) {
        LoopBranchingNodeImpl curLoop = (LoopBranchingNodeImpl)curTop;
        BreakBranchingNode breakNode = new BreakBranchingNodeImpl(this.mGraph);
        connectCurrentWorkingNode(breakNode);
        curWorkingNodeList.clear();
        curLoop.addBreak(breakNode);
      }
      else if (curTop instanceof SwitchBranchingNode) {
        //A break in the switch
        SwitchBranchingNodeImpl curSwitchNode = (SwitchBranchingNodeImpl)curTop;
        SwitchCaseGraph curSwitchGraph = curSwitchNode.getSwitchCaseGraph();
        BreakBranchingNode breakNode = new BreakBranchingNodeImpl(this.mGraph);
        connectCurrentWorkingNode(breakNode);
        GraphNodeUtil.connectGraphNode(breakNode, curSwitchGraph.getExitNode());
        curWorkingNodeList.clear();

      }
      else {
        //Not a switch or a loop. What's in the stack?
        PsiCFGDebugUtil.LOG.info("The Nested stack contains an entry that is" +
                                 "not a loop or a switch: " + curTop.getClass().getSimpleName());

      }
    }
    else {
      //Labeled break.
      LoopBranchingNodeImpl loopNode = lookupLoopNodeFromIdentifier(label);
      if (loopNode != null) {
        BreakBranchingNode breakNode = new BreakBranchingNodeImpl(this.mGraph);
        connectCurrentWorkingNode(breakNode);
        loopNode.addBreak(breakNode);
        curWorkingNodeList.clear();
      }
    }
  }

  /**
   * Build the nodes for the continue statement. Using similar
   * strategy of the break statement.
   * @param statement The continue statement.
   */
  public void dfsPsiContinueStatementBuilder(PsiContinueStatement statement) {
    //Not in a loop. Quit
    if (mNestedStack.isEmpty()) {
      return;
    }
    PsiIdentifier label = statement.getLabelIdentifier();
    if (label == null) {
      //Great, unlabeled continue
      LoopBranchingNodeImpl curLoop = lookForTopMostLoop();
      if (curLoop == null) {
        return;
      }
      ContinueBranchingNode continueNode = new ContinueBranchingNodeImpl(this.mGraph);
      curLoop.addContinue(continueNode);
    }
    else {
      //Labeled continue.
      LoopBranchingNodeImpl loopNode = lookupLoopNodeFromIdentifier(label);
      if (loopNode != null) {
        ContinueBranchingNode continueNode = new ContinueBranchingNodeImpl(this.mGraph);
        loopNode.addContinue(continueNode);
      }
    }
  }

  /**
   * Build the nodes for expression statement.
   * The expression statements are the statements
   * that can be evaluated at runtime to a value.
   * e.g. 3 + 4, a + 2, or return value of a method.
   * @param statement The Expression statement
   * @return The temp local or a reference to the expression.
   */
  public Value dfsExpressionStatementBuilder(PsiExpressionStatement statement) {
    PsiExpression expression = statement.getExpression();
    return dfsExpressionBuilder(expression);
  }

  /**
   * Build the nodes for expression list, basically is a sequence of expressions
   * seperated by comma
   * a ,b
   * @param statement The expression list,
   * @return The temp local or a reference to the last expression
   */
  public Value dfsExpressionListStatementBuilder(PsiExpressionListStatement statement) {
    PsiExpressionList listOfExpressions = statement.getExpressionList();
    PsiExpression[] expressionArray = listOfExpressions.getExpressions();
    Value v = null;
    for (PsiExpression expression : expressionArray) {
      v = dfsExpressionBuilder(expression);
    }
    return v;
  }

  /**
   * Build the node for the return statement.
   * @param statement The return statement.
   */
  public void dfsPsiReturnStmtBuilder(PsiReturnStatement statement) {
    PsiExpression returnValueExpr = statement.getReturnValue();
    //eval the return value
    Value returnVal = null;
    if (returnValueExpr != null) {
      returnVal = dfsExpressionBuilder(returnValueExpr);
    }
    ReturnStmtImpl returnStmtImpl = new ReturnStmtImpl(returnVal, statement);
    //Create the node for the return statement
    GraphNodeImpl returnNode = new GraphNodeImpl(this.mGraph);
    returnNode.getStmtList().add(returnStmtImpl);
    connectCurrentWorkingNode(returnNode);
    //Find the target node. Should be the exit node of the method's exit node
    GraphNode targetExitNode = getMethodExit();
    GraphNodeUtil.connectGraphNode(returnNode, targetExitNode);
    //Statements after the return statement should be consider unreachable.
    curWorkingNodeList.clear();
    checkUnreachable();
  }

  private GraphNode getMethodExit() {
    Graph currentGraph = this.mGraph;
    while (currentGraph != null && (!(currentGraph instanceof MethodGraph))) {
      currentGraph = currentGraph.getParentGraph();
    }
    if (currentGraph instanceof MethodGraph) {
      return currentGraph.getExitNode();
    }
    else {
      return null;
    }
  }

  /**
   * Build the nodes for assignment expression
   * @param expression the assignment expression
   * @return The local or the reference to the assignment expression
   */
  public Value dfsAssignmentExpressionBuilder(PsiAssignmentExpression expression) {
    PsiExpression LExpr = expression.getLExpression();
    PsiExpression RExpr = expression.getRExpression();
    Value LhsValue = dfsLHSExpressionBuilder(LExpr);
    Value RhsValue = dfsExpressionBuilder(RExpr);
    AssignStmtImpl assignStmt =
      new AssignStmtImpl(false, expression, expression.getOperationTokenType());
    assignStmt.setLOp(LhsValue);
    assignStmt.setROp(RhsValue);
    connectGeneratedStmt(assignStmt);
    return LhsValue;
  }

  /**
   * Create a temp local for an expression
   * @param expr The reference to the expression
   * @return The temp local.
   */
  public SynthesizedLocalImpl createSynthesizeTemporalVariable(Value expr) {
    SynthesizedLocalImpl synthesizedLocal =
      new SynthesizedLocalImpl(expr.getType(), expr.getPsiRef().getText(), null);

    AssignStmtImpl synthesizedAssign = new AssignStmtImpl(true, null, JavaTokenType.EQ);
    synthesizedAssign.setLOp(synthesizedLocal);
    synthesizedAssign.setROp(expr);
    synthesizedLocal.setAssignStmt(synthesizedAssign);
    connectGeneratedStmt(synthesizedAssign);
    return synthesizedLocal;
  }


  public Value createRHSStaticRefExpression(PsiType fieldType,
                                            PsiCFGField cfgField,
                                            PsiElement expression) {
    StaticFieldRefImpl staticFieldRef = new StaticFieldRefImpl(fieldType, cfgField, expression);
    SynthesizedLocal synLocal = createSynthesizeTemporalVariable(staticFieldRef);
    return synLocal;
  }

  public Value createLHSStaticRefExpression(PsiType fieldType,
                                            PsiCFGField cfgField,
                                            PsiElement expression) {
    StaticFieldRefImpl staticFieldRef = new StaticFieldRefImpl(fieldType, cfgField, expression);
    return staticFieldRef;
  }

  public Value processLHSRefExprWithTgtPsiFieldQualifierNull(PsiField fieldTarget,
                                                             PsiCFGClass cfgClass,
                                                             PsiCFGField cfgField,
                                                             PsiReferenceExpression expression) {

    //Referring a [this.]field
    //TODO: Inner Class/ Anonymous Class might have difference reference to this.

    //If this field is a static field. Create a static reference.
    if (cfgField.isStatic()) {

      return createLHSStaticRefExpression(fieldTarget.getType(), cfgField, expression);
    }

    //The field may only exist in the super class of
    //Current container class. Therefore, check the
    //container class first.
    PsiCFGClass thisClass = this.containerClass;
    PsiClass thisPsiClass = thisClass.getPsiClass();
    if (thisPsiClass == null) {
      thisPsiClass = fieldTarget.getContainingClass();
    }

    PsiType thisType = retrieveTypeByPsiClass(thisPsiClass);

    ThisRefImpl thisRefImpl = new ThisRefImpl(null, thisClass, thisType);
    InstanceFieldRefImpl instanceFieldRef = new InstanceFieldRefImpl(
      fieldTarget.getType(), cfgField, expression);
    instanceFieldRef.setBase(thisRefImpl);

    return instanceFieldRef;
  }

  public Value processRefExprWithTgtPsiFieldQualifierNull(PsiField fieldTarget,
                                                          PsiCFGClass cfgClass,
                                                          PsiCFGField cfgField,
                                                          PsiReferenceExpression expression) {

    //Referring a [this.]field
    //TODO: Inner Class/ Anonymous Class might have difference reference to this.

    //If this field is a static field. Create a static reference.
    if (cfgField.isStatic()) {
      return createRHSStaticRefExpression(fieldTarget.getType(), cfgField, expression);
    }

    //The field may only exist in the super class of
    //Current container class. Therefore, check the
    //container class first.
    PsiCFGClass thisClass = this.containerClass;
    PsiClass thisPsiClass = thisClass.getPsiClass();
    if (thisPsiClass == null) {
      thisPsiClass = fieldTarget.getContainingClass();
    }

    PsiType thisType = retrieveTypeByPsiClass(thisPsiClass);

    ThisRefImpl thisRefImpl = new ThisRefImpl(null, thisClass, thisType);
    InstanceFieldRefImpl instanceFieldRef = new InstanceFieldRefImpl(
      fieldTarget.getType(), cfgField, expression);
    instanceFieldRef.setBase(thisRefImpl);
    SynthesizedLocal synLocal = createSynthesizeTemporalVariable(instanceFieldRef);
    return synLocal;
  }

  /**
   * Reference handling needs more thoughts. Due to the limitation
   * of the intelliJ. Perhaps we need a implement our own reference
   * resolve system.
   *
   * @param expression
   * @return
   */
  public Value dfsRHSReferenceExpressionBuilder(PsiReferenceExpression expression) {

    //Debug info
    //PsiCFGDebugUtil.debugOutputPsiElement(expression);

    //First, determine if the ReferenceExpression is referring to a local
    PsiElement target = expression.resolve();
    if (target == null) {
      PsiCFGDebugUtil.LOG.warning("ReferenceExpression cannot be resolved: "
                                  + expression.getText());
    }
    if (target instanceof PsiLocalVariable) {
      Local l = resolveLocal((PsiLocalVariable) target);
      //return this.mGraph.getLocalFromPsiLocal((PsiLocalVariable)target);
      if (l == null) {
        return new DummyRef(expression.getType(), expression);
      } else {
        return l;
      }
    }

    //Second, determine if the ReferenceExpression is refering to a param
    if (target instanceof PsiParameter) {
      //The this.mGraph should be a methodGraph or a try catch block to have parameters
      //if (this.mGraph instanceof MethodGraph) {
      //  Param v = ((MethodGraphImpl)this.mGraph).getParamFromPsiParam((PsiParameter)target);
      //  return v;
      //}
      //else {
      //  //TODO: Make necessary changes to support try catch block
      //  PsiCFGDebugUtil.LOG.warning("Refering a param that is not available in this context\n "
      //                           + expression.getText() + "\n");
      //  return new DummyRef(expression.getType(), expression);
      //}
      Param p = resolveParam((PsiParameter)target);
      if (p == null) {
        return new DummyRef(expression.getType(), expression);
      } else {
        return p;
      }
    }
    //So now the reference is referring to an field/ method/class etc
    PsiExpression qualifier = expression.getQualifierExpression();

    //The reference target is a field
    if (target instanceof PsiField) {

      PsiField fieldTarget = (PsiField)target;
      PsiCFGClass cfgClass = mScene.getOrCreateCFGClass(fieldTarget.getContainingClass());
      PsiCFGField cfgField = cfgClass.getField(fieldTarget.getName());

      if (qualifier == null) {
        //Referring a [this.]field
        //TODO: Inner Class/ Anonymous Class might have difference reference to this.
        return processRefExprWithTgtPsiFieldQualifierNull(fieldTarget,
                                                          cfgClass,
                                                          cfgField,
                                                          expression);

      }
      else if (qualifier instanceof PsiThisExpression) {
        //this.sth
        PsiCFGClass thisClass = this.containerClass;
        PsiThisExpression thisPsiExpression = (PsiThisExpression)qualifier;
        ThisRefImpl thisRefImpl = new ThisRefImpl(thisPsiExpression, thisClass, fieldTarget.getType());
        InstanceFieldRefImpl instanceFieldRef = new InstanceFieldRefImpl(
          fieldTarget.getType(), cfgField, expression);
        instanceFieldRef.setBase(thisRefImpl);
        SynthesizedLocal synLocal = createSynthesizeTemporalVariable(instanceFieldRef);
        return synLocal;

      }
      else if (qualifier instanceof PsiSuperExpression) {
        //super.sth
        //Should not happen here
        PsiCFGDebugUtil.LOG.warning("Super.field happened at expression: "
                                    + expression.getText());
        return new DummyRef(expression.getType(), expression);
      }
      else if (qualifier instanceof PsiReferenceExpression) {

        PsiElement resolveOfQualifier = ((PsiReferenceExpression)qualifier).resolve();
        if (resolveOfQualifier instanceof PsiClass) {
          //Qualifier is a class. Resolved reference is a field
          //Consider it is a static reference
          return createRHSStaticRefExpression(fieldTarget.getType(), cfgField, expression);
        }
        else if (resolveOfQualifier instanceof PsiReferenceExpression) {
          //qualifier is a PsiReferenceExpression
          Value qualifierValue =
            dfsRHSReferenceExpressionBuilder((PsiReferenceExpression)resolveOfQualifier);

          InstanceFieldRef instanceFieldRef =
            new InstanceFieldRefImpl(fieldTarget.getType(), cfgField, expression);

          instanceFieldRef.setBase(qualifierValue);
          SynthesizedLocal synLocal = createSynthesizeTemporalVariable(instanceFieldRef);
          return synLocal;

        }
        else if (resolveOfQualifier instanceof PsiLocalVariable) {

          //Qualifier is a local
          //Local.field
          Local local = resolveLocal((PsiLocalVariable) resolveOfQualifier);
          if (local == null) {
            local = new LocalImpl(qualifier.getType(), (PsiLocalVariable)resolveOfQualifier);
          }
          InstanceFieldRef instanceFieldRef =
            new InstanceFieldRefImpl(fieldTarget.getType(), cfgField, expression);

          instanceFieldRef.setBase(local);
          SynthesizedLocal synLocal = createSynthesizeTemporalVariable(instanceFieldRef);
          return synLocal;
        }
        else if (
          resolveOfQualifier instanceof PsiField || resolveOfQualifier instanceof PsiParameter
          ) {

          Value field = dfsRHSReferenceExpressionBuilder((PsiReferenceExpression)qualifier);
          InstanceFieldRef instanceFieldRef =
            new InstanceFieldRefImpl(fieldTarget.getType(), cfgField, expression);

          instanceFieldRef.setBase(field);
          SynthesizedLocal synLocal = createSynthesizeTemporalVariable(instanceFieldRef);
          return synLocal;

        }
        else {
          PsiCFGDebugUtil.LOG.warning("Unknown resolve type of qualifer ");
          PsiCFGDebugUtil.debugOutputPsiElement(resolveOfQualifier);
        }
      }
      else if (qualifier instanceof PsiMethodCallExpression) {
        //a.method().field
        Value methodCallLocal =
          dfsPsiMethodCallExpressionBuilder((PsiMethodCallExpression)qualifier);

        InstanceFieldRefImpl instanceFieldRef =
          new InstanceFieldRefImpl(fieldTarget.getType(), cfgField, expression);

        instanceFieldRef.setBase(methodCallLocal);
        SynthesizedLocal synLocal = createSynthesizeTemporalVariable(instanceFieldRef);
        return synLocal;
      }
      else {
        //Unsupported Qualifier
        PsiCFGDebugUtil.LOG.warning("Unsupported Qualifier in expression: " + expression.getText());
        PsiCFGDebugUtil.debugOutputPsiElement(qualifier);
      }

    }
    else if (target instanceof PsiMethod) {
      PsiCFGDebugUtil.LOG.info("Refering to a method: " + ((PsiMethod)target).getName());
    }
    else {
      PsiCFGDebugUtil.LOG.info("Other circumstances: target of the Reference Expression is : "
                               + target.getClass().getSimpleName());
    }

    DummyRef ref = new DummyRef(expression.getType(), expression);
    return ref;
  }


  /**
   * @param expression
   * @return
   */
  public Value dfsLHSReferenceExpressionBuilder(PsiReferenceExpression expression) {
    PsiElement target = expression.resolve();
    if (target == null) {
      PsiCFGDebugUtil.LOG.warning("ReferenceExpression cannot be resolved: "
                                  + expression.getText());
    }
    if (target instanceof PsiLocalVariable) {
      Local l = resolveLocal((PsiLocalVariable) target);
      //return this.mGraph.getLocalFromPsiLocal((PsiLocalVariable)target);
      if (l == null) {
        return new DummyRef(expression.getType(), expression);
      } else {
        return l;
      }
    }

    //Second, determine if the ReferenceExpression is refering to a param
    if (target instanceof PsiParameter) {

      Param p = resolveParam((PsiParameter)target);
      if (p == null) {
        return new DummyRef(expression.getType(), expression);
      } else {
        return p;
      }
    }

    //So now it refers to a field/method/class
    PsiExpression qualifier = expression.getQualifierExpression();
    if (qualifier == null) {

    }

    if (target instanceof PsiField) {

      PsiField fieldTarget = (PsiField)target;
      PsiCFGClass cfgClass = mScene.getOrCreateCFGClass(fieldTarget.getContainingClass());
      PsiCFGField cfgField = cfgClass.getField(fieldTarget.getName());

      if (qualifier == null) {
        //Referring a [this.]field
        //TODO: Inner Class/ Anonymous Class might have difference reference to this.
        return processLHSRefExprWithTgtPsiFieldQualifierNull(fieldTarget,
                                                             cfgClass,
                                                             cfgField,
                                                             expression);

      }
      else if (qualifier instanceof PsiThisExpression) {
        //this.sth
        PsiCFGClass thisClass = this.containerClass;
        PsiThisExpression thisPsiExpression = (PsiThisExpression)qualifier;
        ThisRefImpl thisRefImpl =
          new ThisRefImpl(thisPsiExpression, thisClass, fieldTarget.getType());

        InstanceFieldRefImpl instanceFieldRef = new InstanceFieldRefImpl(
          fieldTarget.getType(), cfgField, expression);
        instanceFieldRef.setBase(thisRefImpl);
        //SynthesizedLocal synLocal = createSynthesizeTemporalVariable(instanceFieldRef);
        return instanceFieldRef;

      }
      else if (qualifier instanceof PsiSuperExpression) {
        //super.sth
        //Should not happen here
        PsiCFGDebugUtil.LOG.warning("Super.field happened at expression: "
                                    + expression.getText());
        return new DummyRef(expression.getType(), expression);
      }
      else if (qualifier instanceof PsiReferenceExpression) {

        PsiElement resolveOfQualifier = ((PsiReferenceExpression)qualifier).resolve();
        if (resolveOfQualifier instanceof PsiClass) {
          //Qualifier is a class. Resolved reference is a field
          //Consider it is a static reference
          return createLHSStaticRefExpression(fieldTarget.getType(), cfgField, expression);

        }
        else if (resolveOfQualifier instanceof PsiReferenceExpression) {
          //qualifier is a PsiReferenceExpression
          Value qualifierValue =
            dfsRHSReferenceExpressionBuilder((PsiReferenceExpression)resolveOfQualifier);

          InstanceFieldRef instanceFieldRef =
            new InstanceFieldRefImpl(fieldTarget.getType(), cfgField, expression);

          instanceFieldRef.setBase(qualifierValue);
          return instanceFieldRef;

        }
        else if (resolveOfQualifier instanceof PsiLocalVariable) {

          //Qualifier is a local
          //Local.field
          Local local = resolveLocal((PsiLocalVariable) resolveOfQualifier);
          if (local == null) {
            local = new LocalImpl(qualifier.getType(), (PsiLocalVariable)resolveOfQualifier);
          }
          InstanceFieldRef instanceFieldRef =
            new InstanceFieldRefImpl(fieldTarget.getType(), cfgField, expression);

          instanceFieldRef.setBase(local);
          return instanceFieldRef;
        }
        else if (
          resolveOfQualifier instanceof PsiField || resolveOfQualifier instanceof PsiParameter
          ) {

          Value field = dfsRHSReferenceExpressionBuilder((PsiReferenceExpression)qualifier);
          InstanceFieldRef instanceFieldRef =
            new InstanceFieldRefImpl(fieldTarget.getType(), cfgField, expression);

          instanceFieldRef.setBase(field);
          return instanceFieldRef;

        }
        else {
          PsiCFGDebugUtil.LOG.warning("Unknown resolve type of qualifer ");
          PsiCFGDebugUtil.debugOutputPsiElement(resolveOfQualifier);
        }
      }
      else if (qualifier instanceof PsiMethodCallExpression) {
        //a.method().field
        Value methodCallLocal =
          dfsPsiMethodCallExpressionBuilder((PsiMethodCallExpression)qualifier);

        InstanceFieldRefImpl instanceFieldRef =
          new InstanceFieldRefImpl(fieldTarget.getType(), cfgField, expression);

        instanceFieldRef.setBase(methodCallLocal);
        return instanceFieldRef;
      }
      else {
        //Unsupported Qualifier
        PsiCFGDebugUtil.LOG.warning("Unsupported Qualifier in expression: " + expression.getText());
        PsiCFGDebugUtil.debugOutputPsiElement(qualifier);
      }

    }
    else if (target instanceof PsiMethod) {
      PsiCFGDebugUtil.LOG.info("Refering to a method: " + ((PsiMethod)target).getName());
    }
    else {
      PsiCFGDebugUtil.LOG.info("Other circumstances: target of the Reference Expression is : " +
                               target.getClass().getSimpleName());
    }

    DummyRef ref = new DummyRef(expression.getType(), expression);
    return ref;
  }

  /**
   * Handle the PsiPolyadicExpression. Like binOpExpress,
   * in PsiTree. The PolyadicExpression could contain
   * short-circuit logical operators. It is handled in this
   * Method
   *
   * @param polyadicExpress The polyadic expression
   * @return
   */
  public Value dfsPolyadicExpressionBuilder(PsiPolyadicExpression expression) {
    IElementType iOperator = expression.getOperationTokenType();
    if (
      expression.getType() == PsiType.BOOLEAN &&
      (iOperator == JavaTokenType.ANDAND ||
       iOperator == JavaTokenType.OROR)
      ) {
      //Short circuit logical operators.
      ArrayList<GraphNode> returnedWorkingNode = Lists.newArrayList();
      PsiExpression[] expressionArray = expression.getOperands();
      if (iOperator == JavaTokenType.OROR) {
        //Short circuit logical OR
        SynthesizedLocal finalLocal =
          new SynthesizedLocalImpl(PsiType.BOOLEAN, expression.getText(), expression);

        for (int i = 0; i < expressionArray.length; i++) {
          //If this operand is false then evaluate next one
          Value curExprValue = dfsExpressionBuilder(expressionArray[i]);
          AssignStmtImpl synAssign = new AssignStmtImpl(true, null, JavaTokenType.EQ);
          synAssign.setLOp(finalLocal);
          synAssign.setROp(curExprValue);
          GraphNodeImpl assignNode = new GraphNodeImpl(this.mGraph);
          assignNode.getStmtList().add(synAssign);
          returnedWorkingNode.add(assignNode);
          //CreateConditionNode
          if (i != expressionArray.length - 1) {
            ConditionCheckNodeImpl curCond = new ConditionCheckNodeImpl(this.mGraph, curExprValue);
            connectCurrentWorkingNode(curCond);
            curWorkingNodeList.clear();
            curWorkingNodeList.add(curCond.getFalseBranch());
            GraphNodeUtil.connectGraphNode(curCond.getTrueBranch(), assignNode);
          }
          else {
            //Last one
            for (GraphNode parent : curWorkingNodeList) {
              GraphNodeUtil.connectGraphNode(parent, assignNode);
            }
          }
        }
        curWorkingNodeList.clear();
        curWorkingNodeList.addAll(returnedWorkingNode);
        return finalLocal;
      }//End of OROR

      if (iOperator == JavaTokenType.ANDAND) {
        //Short circuit logical AND
        SynthesizedLocal finalLocal =
          new SynthesizedLocalImpl(PsiType.BOOLEAN, expression.getText(), expression);

        for (int i = 0; i < expressionArray.length; i++) {
          //If this operand is true then evaluate next one
          Value curExprValue = dfsExpressionBuilder(expressionArray[i]);
          AssignStmtImpl synAssign = new AssignStmtImpl(true, null, JavaTokenType.EQ);
          synAssign.setLOp(finalLocal);
          synAssign.setROp(curExprValue);
          GraphNodeImpl assignNode = new GraphNodeImpl(this.mGraph);
          assignNode.getStmtList().add(synAssign);
          returnedWorkingNode.add(assignNode);
          //CreateConditionNode
          if (i != expressionArray.length - 1) {
            //Not last one
            ConditionCheckNodeImpl curCond = new ConditionCheckNodeImpl(this.mGraph, curExprValue);
            connectCurrentWorkingNode(curCond);
            curWorkingNodeList.clear();
            curWorkingNodeList.add(curCond.getTrueBranch());
            GraphNodeUtil.connectGraphNode(curCond.getFalseBranch(), assignNode);
          }
          else {
            //Last one
            for (GraphNode parent : curWorkingNodeList) {
              GraphNodeUtil.connectGraphNode(parent, assignNode);
            }
          }
        }
        curWorkingNodeList.clear();
        curWorkingNodeList.addAll(returnedWorkingNode);
        return finalLocal;
      }//End of ANDAND
    }// End of short circuit Logical operators.
    PolyadicExprImpl mPolyadicExpr = new PolyadicExprImpl(iOperator, expression);
    PsiExpression[] allOperands = expression.getOperands();
    for (PsiExpression curPsiExpr : allOperands) {
      Value v = dfsExpressionBuilder(curPsiExpr);
      mPolyadicExpr.addOperand(v);
    }
    SynthesizedLocalImpl polyadicLocal = createSynthesizeTemporalVariable(mPolyadicExpr);
    return polyadicLocal;
  }

  /**
   * Build nodes for expressions like expr OP expr.
   * There are special cases: expr LogicalOP expr.
   * The AND and OR is short-circuit. They are processed in
   * this method.
   *
   * @param binExpression The expression
   * @return The local or the reference to the expression
   */
  public Value dfsBinaryExpressionBuilder(PsiBinaryExpression binExpression) {
    PsiExpression opL = binExpression.getLOperand();
    PsiExpression opR = binExpression.getROperand();
    IElementType iOperator = binExpression.getOperationTokenType();
    if (
      (binExpression.getType() == PsiType.BOOLEAN) &&
      (iOperator == JavaTokenType.ANDAND ||
       iOperator == JavaTokenType.OROR
      )
      ) {
      //The expression is using short circuit boolean operators
      if (iOperator == JavaTokenType.OROR) {
        //Short-circuit logical OR. evaluate Lop first
        //If the L Operand is already true. The R Operand does not need to be
        //evaluated.
        Value LExpr = dfsExpressionBuilder(opL);
        ConditionCheckNode condCheckNode = new ConditionCheckNodeImpl(mGraph, LExpr);
        connectCurrentWorkingNode(condCheckNode);
        //If the L Operand is false. Evaluate the R operand
        curWorkingNodeList.clear();
        curWorkingNodeList.add(condCheckNode.getFalseBranch());
        Value RExpr = dfsExpressionBuilder(opR);
        //Create synthesized local
        SynthesizedLocal synLocal =
          new SynthesizedLocalImpl(PsiType.BOOLEAN, binExpression.getText(), binExpression);
        //Create 2 assignment statement
        //First: true branch
        AssignStmtImpl trueBranch = new AssignStmtImpl(true, null, JavaTokenType.EQ);
        trueBranch.setLOp(synLocal);

        //Maybe it can be changed to a True constant in this case?
        trueBranch.setROp(LExpr);
        GraphNodeImpl LBranchNode = new GraphNodeImpl(this.mGraph);
        LBranchNode.getStmtList().add(trueBranch);
        GraphNodeUtil.connectGraphNode(condCheckNode.getTrueBranch(), LBranchNode);

        //Second: false branch
        AssignStmtImpl falseBranch = new AssignStmtImpl(true, null, JavaTokenType.EQ);
        falseBranch.setLOp(synLocal);
        falseBranch.setROp(RExpr);
        GraphNodeImpl RBranchNode = new GraphNodeImpl(this.mGraph);
        RBranchNode.getStmtList().add(falseBranch);
        //GraphNodeUtil.connectGraphNode(condCheckNode.getFalseBranch(),);
        for (GraphNode parentNode : curWorkingNodeList) {
          GraphNodeUtil.connectGraphNode(parentNode, RBranchNode);
        }
        curWorkingNodeList.clear();
        curWorkingNodeList.add(LBranchNode);
        curWorkingNodeList.add(RBranchNode);
        return synLocal;
      }

      if (iOperator == JavaTokenType.ANDAND) {
        //Short-circuit logical AND. evaluate Lop first
        Value LExpr = dfsExpressionBuilder(opL);
        ConditionCheckNode condCheckNode = new ConditionCheckNodeImpl(mGraph, LExpr);
        connectCurrentWorkingNode(condCheckNode);

        //If the L Operand is true. Evaluate the R operand
        curWorkingNodeList.clear();
        curWorkingNodeList.add(condCheckNode.getTrueBranch());
        Value RExpr = dfsExpressionBuilder(opR);

        //Create synthesized local
        SynthesizedLocal synLocal =
          new SynthesizedLocalImpl(PsiType.BOOLEAN, binExpression.getText(), binExpression);
        //Create 2 assignment statement
        //First false branch
        AssignStmtImpl falseBranch = new AssignStmtImpl(true, null, JavaTokenType.EQ);
        falseBranch.setLOp(synLocal);
        //Can be a constant False
        falseBranch.setROp(LExpr);
        GraphNodeImpl LBranchNode = new GraphNodeImpl(this.mGraph);
        LBranchNode.getStmtList().add(falseBranch);
        GraphNodeUtil.connectGraphNode(condCheckNode.getFalseBranch(), LBranchNode);

        //Second: true branch
        AssignStmtImpl trueBranch = new AssignStmtImpl(true, null, JavaTokenType.EQ);
        trueBranch.setLOp(synLocal);
        trueBranch.setROp(RExpr);
        GraphNodeImpl RBranchNode = new GraphNodeImpl(this.mGraph);
        RBranchNode.getStmtList().add(trueBranch);
        for (GraphNode parentNode : curWorkingNodeList) {
          GraphNodeUtil.connectGraphNode(parentNode, RBranchNode);
        }
        curWorkingNodeList.clear();
        curWorkingNodeList.add(LBranchNode);
        curWorkingNodeList.add(RBranchNode);
        return synLocal;
      }
    } // End of short-circuit logic
    //The expression is not using boolean operators
    //Or not using short circuit boolean operators.
    BinopExprImpl binopExpr = new BinopExprImpl(binExpression);
    Value vOp1 = dfsExpressionBuilder(opL);
    Value vOp2 = dfsExpressionBuilder(opR);
    binopExpr.setOp1(vOp1);
    binopExpr.setOp2(vOp2);
    binopExpr.setOperator(binExpression.getOperationTokenType());
    SynthesizedLocal binopLocal = createSynthesizeTemporalVariable(binopExpr);
    return binopLocal;
  }

  public Value dfsParenthesizedExpression(PsiParenthesizedExpression expression) {
    PsiExpression expr = expression.getExpression();
    Value v = dfsExpressionBuilder(expr);
    if (!(v instanceof SynthesizedLocal)) {
      createSynthesizeTemporalVariable(v);
    }
    return v;
  }

  public Value dfsLiteralExpressionBuilder(PsiLiteralExpression literalExpression) {
    ConstantImpl constExpr = new ConstantImpl(literalExpression);
    return constExpr;
  }


  /**
   * Used when processing the assignment statement.
   * The LHS PsiExpression should not be a local value that holds the
   * value of the object. It should be a reference.
   *
   * @param expression
   * @return
   */
  public Value dfsLHSExpressionBuilder(PsiExpression expression) {
    if (expression instanceof PsiReferenceExpression) {
      return dfsLHSReferenceExpressionBuilder((PsiReferenceExpression) expression);
    }
    return dfsExpressionBuilder(expression);
  }

  public Value dfsExpressionBuilder(PsiExpression expression) {
    if (expression instanceof PsiBinaryExpression) {
      return dfsBinaryExpressionBuilder((PsiBinaryExpression)expression);
    }
    else if (expression instanceof PsiPolyadicExpression) {
      return dfsPolyadicExpressionBuilder((PsiPolyadicExpression)expression);
    }
    else if (expression instanceof PsiLiteralExpression) {
      return dfsLiteralExpressionBuilder((PsiLiteralExpression)expression);
    }
    else if (expression instanceof PsiParenthesizedExpression) {
      return dfsParenthesizedExpression((PsiParenthesizedExpression)expression);
    }
    else if (expression instanceof PsiAssignmentExpression) {
      return dfsAssignmentExpressionBuilder((PsiAssignmentExpression)expression);
    }
    else if (expression instanceof PsiReferenceExpression) {
      return dfsRHSReferenceExpressionBuilder((PsiReferenceExpression)expression);
    }
    else if (expression instanceof PsiMethodCallExpression) {
      return dfsPsiMethodCallExpressionBuilder((PsiMethodCallExpression)expression);
    }
    else if (expression instanceof PsiNewExpression) {
      return dfsPsiNewExpressionBuilder((PsiNewExpression)expression);
    }
    else if (expression instanceof PsiConditionalExpression) {
      return dfsPsiConditionalExpressionBuilder((PsiConditionalExpression)expression);
    }
    else if (expression instanceof PsiInstanceOfExpression) {
      return dfsPsiInstanceOfExpressionBuilder((PsiInstanceOfExpression)expression);
    }
    else if (expression instanceof PsiThisExpression) {
      return dfsThisExpressionBuilder((PsiThisExpression)expression);
    }
    else if (expression instanceof PsiSuperExpression) {
      //TODO: SuperExpression
      return new DummyRef(expression.getType(), expression);
    }
    else if (expression instanceof PsiArrayInitializerExpression) {
      //TODO: ArrayInitExpr
      return new DummyRef(expression.getType(), expression);
    }
    else if (expression instanceof PsiTypeCastExpression) {
      return dfsPsiTypeCastExpressionBuilder((PsiTypeCastExpression)expression);
    }
    else if (expression instanceof PsiArrayAccessExpression) {
      //TODO: Test ArrayAccessExpr
      return dfsRHSPsiArrayAccessExpressionBuilder((PsiArrayAccessExpression)expression);
    }
    else if (expression instanceof PsiPostfixExpression) {
      return dfsPsiPostfixExpressionBuilder((PsiPostfixExpression)expression);
    }
    else if (expression instanceof PsiPrefixExpression) {
      return dfsPsiPrefixExpressionBuilder((PsiPrefixExpression)expression);
    }
    else if (expression instanceof PsiClassObjectAccessExpression) {
      //TODO: String.class etc
      return new DummyRef(expression.getType(), expression);
    }
    else if (expression instanceof PsiLambdaExpression) {

      return dfsPsiLambdaExpressionBuilder((PsiLambdaExpression)expression);
    }
    else {
      PsiCFGDebugUtil.LOG.warning("ExpressionType: " +
                                  expression.getClass().getSimpleName() +
                                  " not Implemented");

      return new DummyRef(expression.getType(), expression);
    }
  }

  private boolean isQualifierPsiClass(PsiExpression qualifier) {
    if (qualifier == null || (!(qualifier instanceof PsiReferenceExpression))) {
      return false;
    }
    PsiReferenceExpression refExpr = (PsiReferenceExpression)qualifier;
    PsiElement resolveType = refExpr.resolve();
    if (resolveType == null || (!(resolveType instanceof PsiClass))) {
      return false;
    }
    return true;
  }

  /**
   * According to Java Language Specification
   * Arguments in a method or constructor are evaluated from left to right.
   * Java Language Specification 15.7.4
   *
   * @param paramList
   * @return
   */
  private Value[] parseMethodCallParams(PsiExpressionList arguments) {
    if (arguments == null) {
      return Value.EMPTY_ARRAY;
    }
    PsiExpression[] paramList = arguments.getExpressions();
    Value[] returnArray = new Value[paramList.length];
    for (int i = 0; i < paramList.length; i++) {
      PsiExpression currentParam = paramList[i];
      Value currentParamValue = null;
      if (currentParam instanceof PsiLambdaExpression) {
        currentParamValue = dfsPsiLambdaExpressionBuilder((PsiLambdaExpression)currentParam);
      }
      else {
        currentParamValue = dfsExpressionBuilder(currentParam);
      }
      returnArray[i] = currentParamValue;
    }
    return returnArray;
  }

  /**
   * The purpose of this method is to transform ()->{} in method invocation
   * or assignment expression into new InterfaceName(Type Arg){ @Override Method(){} };
   *
   * @param expression
   * @return
   */
  public Value dfsPsiLambdaExpressionBuilder(PsiLambdaExpression expression) {

    PsiType interfaceType = expression.getFunctionalInterfaceType();
    PsiClass interfaceClassRef = null;
    if (interfaceType == null) {
      //The SAM type interface cannot be found by intelliJ
      //There is no way to resolve the SAM type by ourselves.
      //Just return a dummy here.
      PsiCFGDebugUtil.LOG.warning("Lambda Expression: "
                                  + expression.getText() + " cannot be resolved");
      return new DummyRef(expression.getType(), expression);
    }

    if (interfaceType instanceof PsiClassType) {
      interfaceClassRef = ((PsiClassType)interfaceType).resolve();
    }
    else {
      PsiCFGDebugUtil.LOG.warning("Lambda Expression: "
                                  + expression.getText() + " type is not interface");
      PsiCFGDebugUtil.LOG.warning("Type: " + interfaceType.getClass().getSimpleName());
      return new DummyRef(expression.getType(), expression);
    }

    //Create the wrapper class for the lambda expression
    PsiCFGClass lambdaWrapperClass = mScene.createLambdaAnonymousClass(
      expression, interfaceClassRef, this.containerClass);

    //Create new expression
    NewExprImpl newExprImpl = new NewExprImpl(interfaceType, expression);
    newExprImpl.setBaseClass(lambdaWrapperClass);
    //Lambda expression does not have constructor arguments

    SynthesizedLocal synLocal = createSynthesizeTemporalVariable(newExprImpl);
    return synLocal;

  }

  public Value dfsPsiNewExpressionBuilder(PsiNewExpression expression) {
    PsiExpressionList argumentList = expression.getArgumentList();
    Value[] paramValueArray = parseMethodCallParams(argumentList);
    PsiType newType = expression.getType();

    PsiClass classOfNewInstance;
    PsiCFGClass cfgClassOfNewInstance;


    PsiAnonymousClass anonymousClass = expression.getAnonymousClass();

    //Determine if it is a array initiation.
    if (newType instanceof PsiArrayType) {
      //TODO: Change it to use newArray

      PsiArrayType newArrayType = (PsiArrayType)newType;
      PsiType baseType = newArrayType.getComponentType();
      NewExprImpl newArrayExprImpl = new NewExprImpl(newArrayType, expression);
      newArrayExprImpl.setArray();
      //TODO: process its initiation
      SynthesizedLocal synLocal = createSynthesizeTemporalVariable(newArrayExprImpl);
      return synLocal;
    }

    if (anonymousClass != null) {

      //PsiCFGDebugUtil.LOG.warning("Currently AnonymousClass is not handled");
      cfgClassOfNewInstance = mScene.getOrCreateNestedClass(anonymousClass,
                                                            this.containerClass,
                                                            retrieveDeclaringMethod(),
                                                            this.mGraph);

    }
    else {
      PsiJavaCodeReferenceElement classReference = expression.getClassReference();
      if (classReference == null) {
        PsiCFGDebugUtil.LOG.warning("classReference of the new expression is null");
        PsiCFGDebugUtil.debugOutputPsiElement(expression);
        System.out.println(expression.getText());

        PsiExpression[] dimisionExpressions = expression.getArrayDimensions();
        System.out.println("Dimision: " + dimisionExpressions.length);
        System.out.println("Type: " +
                           newType.getCanonicalText() +
                           " " + newType.getClass().getSimpleName());

        throw new RuntimeException("classReference in dfsNewExpressionBuilder is null");
      }

      PsiElement resolvedExpression = classReference.resolve();
      if (resolvedExpression == null || (!(resolvedExpression instanceof PsiClass))) {
        PsiCFGDebugUtil.LOG.warning("Cannot resolve the class in the new expression in "
                                    + expression.getText());

        return new DummyRef(expression.getType(), expression);
      }

      classOfNewInstance = (PsiClass)resolvedExpression;
      cfgClassOfNewInstance = mScene.getOrCreateCFGClass(classOfNewInstance);

    }

    //Resolve Constructor
    PsiMethod constructorMethod = expression.resolveConstructor();


    NewExprImpl newExprImpl = new NewExprImpl(expression.getType(), expression);
    newExprImpl.setBaseClass(cfgClassOfNewInstance);

    //Add Args/Param to the new expression
    ArrayList<Value> argsList = newExprImpl.getArgsList();
    for (Value v : paramValueArray) {
      argsList.add(v);
    }

    if (constructorMethod != null) {
      PsiClass declaringClassRef = constructorMethod.getContainingClass();
      PsiCFGClass construtorCFGClass = mScene.getOrCreateCFGClass(declaringClassRef);
      PsiCFGMethod constructorCFGMethod = construtorCFGClass.getMethod(constructorMethod);
      if (constructorCFGMethod == null) {
        PsiCFGDebugUtil.LOG.warning("Cannot resolve the constructor for " +
                                    constructorMethod.getName());

      }
      else {
        newExprImpl.setConstrctorInvocation(constructorCFGMethod);
      }
    }

    SynthesizedLocal synLocal = createSynthesizeTemporalVariable(newExprImpl);
    return synLocal;
  }

  private void experimentalLambdaSolver(PsiExpressionList exprList) {
    PsiExpression[] args = exprList.getExpressions();
    for (PsiExpression e : args) {
      if (e instanceof PsiLambdaExpression) {
        PsiLambdaExpression lE = (PsiLambdaExpression)e;
        lE.getParameterList();
      }
    }
  }

  public Value createStaticInvocation(PsiCFGMethod cfgMethod,
                                      PsiCFGClass cfgClass,
                                      PsiMethod resolvedMethod,
                                      Value[] argsValueArray,
                                      PsiElement expression) {
    StaticInvokeExprImpl staticIvk =
      new StaticInvokeExprImpl(cfgMethod, resolvedMethod.getReturnType(), expression);

    staticIvk.setBaseClass(cfgClass);
    for (Value v : argsValueArray) {
      staticIvk.addArg(v);
    }
    SynthesizedLocalImpl synLocal = createSynthesizeTemporalVariable(staticIvk);
    return synLocal;
  }

  public Value dfsRHSPsiArrayAccessExpressionBuilder(PsiArrayAccessExpression expression) {
    PsiExpression arrayPsiExpr = expression.getArrayExpression();
    PsiExpression indexPsiExpr = expression.getIndexExpression();
    Value arrayValue = dfsExpressionBuilder(arrayPsiExpr);
    Value indexValue = dfsExpressionBuilder(indexPsiExpr);
    ArrayAccessRefImpl arrayAccessRef = new ArrayAccessRefImpl(expression.getType(), expression);
    arrayAccessRef.setBase(arrayValue);
    arrayAccessRef.setBase(indexValue);
    SynthesizedLocal synLocal = createSynthesizeTemporalVariable(arrayAccessRef);
    return synLocal;
  }

  public Value dfsPsiMethodCallExpressionBuilder(PsiMethodCallExpression expression) {

    PsiExpressionList argsList = expression.getArgumentList();
    PsiReferenceExpression methodRef = expression.getMethodExpression();
    PsiExpression qualifier = methodRef.getQualifierExpression();
    Value[] argsValueArray = parseMethodCallParams(argsList);
    //experimentalLambdaSolver(argsList);

    if (methodRef instanceof PsiMethodReferenceExpression) {
      PsiMethodReferenceExpression psiMethodRef = (PsiMethodReferenceExpression)methodRef;
      PsiCFGDebugUtil.LOG.info("The MethodCallExpression contains a PsiMethodReferenceExpression");
    }
    //In the PsiMethodCallExpression, The PsiReferenceExpression Should always
    //refer to the PsiMethod it should invoke.
    PsiElement resolvedElement = methodRef.resolve();
    if (resolvedElement instanceof PsiMethod) {
      //The resolvedElement is a PsiMethod. As expected
      PsiMethod resolvedMethod = (PsiMethod)resolvedElement;
      //Check if it is a static invocation
      //If it is a static invocation. The qualifier should be PsiReferenceExpress
      //That refer to a PsiClass
      PsiClass declaringClass = resolvedMethod.getContainingClass();
      PsiCFGClass cfgClass = mScene.getOrCreateCFGClass(declaringClass);
      PsiCFGMethod cfgMethod = cfgClass.getMethod(resolvedMethod);
      if (isQualifierPsiClass(qualifier) || cfgMethod.isStatic()) {
        //It is a static invocation
        return createStaticInvocation(cfgMethod,
                                      cfgClass,
                                      resolvedMethod,
                                      argsValueArray,
                                      expression);
      }
      else if (qualifier == null) {
        //It is an instance invocation
        //But without a qualifier. So it should be a this invocation or a static invocation

        if (cfgMethod.isStatic()) {
          return createStaticInvocation(cfgMethod,
                                        cfgClass,
                                        resolvedMethod,
                                        argsValueArray,
                                        expression);
        }

        PsiType thisType = retrieveTypeByPsiClass(this.containerClass.getPsiClass());

        ThisRefImpl synThisRef = new ThisRefImpl(null, this.containerClass, thisType);
        InstanceInvokeExprImpl instanceInvoke =
          new InstanceInvokeExprImpl(cfgMethod, resolvedMethod.getReturnType(), expression);

        instanceInvoke.setBase(synThisRef);
        for (Value v : argsValueArray) {
          instanceInvoke.addArg(v);
        }
        SynthesizedLocal synLocal = createSynthesizeTemporalVariable(instanceInvoke);
        return synLocal;
      }
      else if (qualifier instanceof PsiReferenceExpression) {
        //The method invocation is obj.method()
        Value objLocal = dfsRHSReferenceExpressionBuilder((PsiReferenceExpression)qualifier);
        InstanceInvokeExprImpl instanceInvoke =
          new InstanceInvokeExprImpl(cfgMethod, resolvedMethod.getReturnType(), expression);

        instanceInvoke.setBase(objLocal);
        for (Value v : argsValueArray) {
          instanceInvoke.addArg(v);
        }

        if (objLocal == null) {
          throw new RuntimeException("objectLocal in MethodCallExpression is null");
        }

        PsiType baseType = objLocal.getType();

        if (baseType instanceof PsiClassType) {
          PsiClass classRef = ((PsiClassType)baseType).resolve();
          mScene.getOrCreateCFGClass(classRef);
        }
        SynthesizedLocal synLocal = createSynthesizeTemporalVariable(instanceInvoke);
        return synLocal;
      } else {
        //Unexpected
        PsiCFGDebugUtil.LOG.warning(
          "Did not recognize PsiMethodCallExpression: " + expression.getText() + " of type " + qualifier.getClass().getName());
      }
    }
    else {
      //A method call expression without a reference to the method.
      //Unexpected
      PsiCFGDebugUtil.LOG.warning("Cannot resolve PsiMethodCallExpression e to PsiMethod: "
                                  + expression.getText() + "   "
                                  + (resolvedElement == null ? "null" : resolvedElement.getClass().getName()));

    }
    return new DummyRef(expression.getType(), resolvedElement);
  }

  public Value dfsPsiConditionalExpressionBuilder(PsiConditionalExpression expression) {
    PsiExpression conditionCheckExpression = expression.getCondition();
    PsiExpression trueBranchExpression = expression.getThenExpression();
    PsiExpression falseBranchExpression = expression.getElseExpression();

    //Note in condition express, the type of two branch might not branch.
    //There should be a method to determine the correct type.
    //Currently we just use the type from the PsiExpression

    Value conditionCheckExpr = dfsExpressionBuilder(conditionCheckExpression);
    ConditionCheckNodeImpl curConditionCheckNode =
      new ConditionCheckNodeImpl(this.mGraph, conditionCheckExpr);

    connectCurrentWorkingNode(curConditionCheckNode);
    SynthesizedLocalImpl synLocal =
      new SynthesizedLocalImpl(expression.getType(), expression.getText(), expression);

    //Evaluate True Branch
    curWorkingNodeList.clear();
    curWorkingNodeList.add(curConditionCheckNode.getTrueBranch());
    Value trueBrachValue = dfsExpressionBuilder(trueBranchExpression);
    AssignStmtImpl trueBranchAssign = new AssignStmtImpl(true, null, JavaTokenType.EQ);
    trueBranchAssign.setLOp(synLocal);
    trueBranchAssign.setROp(trueBrachValue);
    GraphNodeImpl trueBranchAssignNode = new GraphNodeImpl(this.mGraph);
    trueBranchAssignNode.getStmtList().add(trueBranchAssign);
    connectCurrentWorkingNode(trueBranchAssignNode);

    //Evaluate False Branch
    curWorkingNodeList.clear();
    curWorkingNodeList.add(curConditionCheckNode.getFalseBranch());
    Value falseBranchValue = dfsExpressionBuilder(falseBranchExpression);
    AssignStmtImpl falseBranchAssign = new AssignStmtImpl(true, null, JavaTokenType.EQ);
    falseBranchAssign.setLOp(synLocal);
    falseBranchAssign.setROp(falseBranchValue);
    GraphNodeImpl falseBranchAssignNode = new GraphNodeImpl(this.mGraph);
    falseBranchAssignNode.getStmtList().add(falseBranchAssign);
    connectCurrentWorkingNode(falseBranchAssignNode);

    //Setup curWorkingNodeList
    curWorkingNodeList.clear();
    curWorkingNodeList.add(trueBranchAssignNode);
    curWorkingNodeList.add(falseBranchAssignNode);
    return synLocal;
  }

  /**
   * Handle a instanceof ClassB
   * l = a instanceof ClassB
   * return l
   *
   * @param resultArray
   * @param expression
   * @return
   */
  public Value dfsPsiInstanceOfExpressionBuilder(PsiInstanceOfExpression expression) {
    PsiType checkedType = expression.getCheckType().getType();
    PsiExpression checkedExpr = expression.getOperand();
    Value checkedVal = dfsExpressionBuilder(checkedExpr);
    InstanceOfExpr curInstanceOfExpr = new InstanceOfExprImpl(checkedType, expression);
    curInstanceOfExpr.setOp(checkedVal);
    Value synLocal = createSynthesizeTemporalVariable(curInstanceOfExpr);
    return synLocal;
  }

  public Value dfsPsiTypeCastExpressionBuilder(PsiTypeCastExpression expression) {
    PsiType type = expression.getType();
    Value castedVal = dfsExpressionBuilder(expression.getOperand());
    CastExprImpl castExpr = new CastExprImpl(type, expression);
    castExpr.setOp(castedVal);
    LocalImpl synLocal = createSynthesizeTemporalVariable(castExpr);
    return synLocal;
  }

  /**
   * Temp work around
   * Handle the case that is a ++
   * Translate to l = a; l2 = l + 1; a = l2; return l;
   *
   * @param resultArray
   * @param expression
   * @return
   */
  public Value dfsPsiPostfixExpressionBuilder(PsiPostfixExpression expression) {
    PsiExpression valuePsiExpression = expression.getOperand();
    Value valueExpr = dfsLHSExpressionBuilder(valuePsiExpression);
    SynthesizedLocal retLocal =
      new SynthesizedLocalImpl(valuePsiExpression.getType(), expression.getText(), null);

    PostfixExprImpl postfixExpr =
      new PostfixExprImpl(expression,
                          expression.getOperationTokenType(),
                          valueExpr,
                          expression.getType());

    AssignStmtImpl synAssign = new AssignStmtImpl(true, null, JavaTokenType.EQ);
    synAssign.setLOp(retLocal);
    synAssign.setROp(postfixExpr);
    GraphNodeImpl prefixNode = new GraphNodeImpl(this.mGraph);
    prefixNode.getStmtList().add(synAssign);
    connectCurrentWorkingNode(prefixNode);
    return retLocal;
  }

  /**
   * Temp work around
   * Handle the case that is ++ a
   * Translate to l = a; l2 = l + 1; a = l2 return l2
   *
   * @param resultArray
   * @param expression
   * @return
   */
  public Value dfsPsiPrefixExpressionBuilder(PsiPrefixExpression expression) {
    PsiExpression valuePsiExpression = expression.getOperand();
    Value valueExpr = dfsLHSExpressionBuilder(valuePsiExpression);
    SynthesizedLocal retLocal =
      new SynthesizedLocalImpl(valuePsiExpression.getType(), expression.getText(), null);

    PrefixExprImpl prefixExpr =
      new PrefixExprImpl(expression,
                         expression.getOperationTokenType(),
                         valueExpr,
                         expression.getType());

    AssignStmtImpl synAssign = new AssignStmtImpl(true, null, JavaTokenType.EQ);
    synAssign.setLOp(retLocal);
    synAssign.setROp(prefixExpr);
    GraphNodeImpl prefixNode = new GraphNodeImpl(this.mGraph);
    prefixNode.getStmtList().add(synAssign);
    connectCurrentWorkingNode(prefixNode);
    return retLocal;
  }

  public Value dfsThisExpressionBuilder(PsiThisExpression expression) {

    PsiType thisType = expression.getType();
    PsiClassType classType = null;
    PsiCFGClass thisCFGClass = this.containerClass;
    if (thisType instanceof PsiClassType) {
      classType = (PsiClassType)thisType;
    }
    else {
      PsiCFGDebugUtil.LOG.warning("PsiThisExpression's type is NOT classType :"
                                  + thisType.getClass().getSimpleName());
    }
    if (classType != null) {
      PsiClass classPsiRef = classType.resolve();
      thisCFGClass = mScene.getOrCreateCFGClass(classPsiRef);
    }

    ThisRefImpl thisRef = new ThisRefImpl(expression, thisCFGClass, thisType);
    return thisRef;
  }

  /**
   * Declaration Statement will not return a value, therefore always return null;
   *
   * @param resultArray
   * @param currentDeclStmt
   * @return
   */
  public Value dfsDeclarationStatementBuilder(PsiDeclarationStatement currentDeclStmt) {

    PsiElement[] retElements = currentDeclStmt.getDeclaredElements();
    for (PsiElement curElement : retElements) {
      if (curElement == null) {
        PsiCFGDebugUtil.LOG.warning("element in DeclarationStatement "
                                    + currentDeclStmt.getText() + " is null");
        continue;
      }

      if (curElement instanceof PsiLocalVariable) {
        //So it is a local variable
        PsiLocalVariable curLocal = (PsiLocalVariable)curElement;
        PsiType localType = curLocal.getType();
        //Generate the decl statement
        DeclarationStmtImpl newDecl = new DeclarationStmtImpl(localType, curLocal, currentDeclStmt);
        connectGeneratedStmt(newDecl);
        if (this.mGraph instanceof BlockGraph) {
          ((BlockGraph)(this.mGraph)).addLocal(curLocal, (LocalImpl)newDecl.getLocal());
        }

        if (curLocal.hasInitializer()) {
          //Generate Statement for the initializer
          PsiExpression initializer = curLocal.getInitializer();
          Value initExpr = dfsExpressionBuilder(initializer);

          AssignStmtImpl initializerStmt = new AssignStmtImpl(true, null, JavaTokenType.EQ);
          LocalImpl localExpr = (LocalImpl)newDecl.getLocal();
          initializerStmt.setROp(initExpr);
          initializerStmt.setLOp(localExpr);
          connectGeneratedStmt(initializerStmt);
        }
      }
      else if (curElement instanceof PsiClass) {
        //It declares a nested class
        mScene.getOrCreateNestedClass((PsiClass) curElement,
                                      this.containerClass,
                                      retrieveDeclaringMethod(),
                                      this.mGraph);
      }
      else {
        PsiCFGDebugUtil.LOG.warning("element " + curElement.getText() + " in DeclarationStmt "
                                    + currentDeclStmt.getText() + " cannot be resolved");
      }
    }
    return null;
  }

  private GraphNode connectGeneratedStmt(Stmt stmt) {
    GraphNodeImpl newNode = new GraphNodeImpl(this.mGraph);
    newNode.getStmtList().add(stmt);
    if (curWorkingNodeList.isEmpty()) {
      curWorkingNodeList.add(this.mGraph.getUnreachableNodeEntry());
    }
    for (GraphNode parentNode : curWorkingNodeList) {
      GraphNodeUtil.connectGraphNode(parentNode, newNode);
    }
    curWorkingNodeList.clear();
    curWorkingNodeList.add(newNode);

    //Check if this node contains invocation
    if (stmt.containsInvokeExpr()) {
      newNode.setInvocation();
      mScene.addInvocationNode(newNode);
    }

    if (stmt instanceof AssignStmt) {
      Value rOP = ((AssignStmt)stmt).getROp();
      if (rOP instanceof NewExpr) {
        NewExpr newExpr = (NewExpr)rOP;
        if (((NewExpr)rOP).containsConstructorInvocation()) {
          newNode.setInvocation();
          mScene.addInvocationNode(newNode);
        }
      }
    }
    return newNode;
  }

  private void checkUnreachable() {
    if (curWorkingNodeList.isEmpty()) {
      curWorkingNodeList.add(this.mGraph.getUnreachableNodeEntry());
    }
  }

  private void connectCurrentWorkingNode(GraphNode node) {
    checkUnreachable();
    for (GraphNode parent : curWorkingNodeList) {
      parent.addOut(node);
      node.addIn(parent);
    }
    curWorkingNodeList.clear();
    curWorkingNodeList.add(node);
  }
}
