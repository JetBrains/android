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
package com.android.tools.idea.experimental.codeanalysis.datastructs.graph.impl;

import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.BlockGraph;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.Graph;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.EntryNode;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.ExitNode;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.GraphNode;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.PlaceholderNode;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.impl.BlockGraphEntryNodeImpl;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.impl.BlockGraphExitNodeImpl;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.impl.PlaceholderNodeImpl;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.Local;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.Param;
import com.google.common.collect.Maps;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiStatement;
import java.util.ArrayList;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public class BlockGraphImpl implements BlockGraph {
  protected EntryNode mEntryNode;

  protected ExitNode mExitNode;

  protected PlaceholderNode mUnreachableNode;

  protected BlockGraph mParentGraph;

  protected Map<PsiLocalVariable, Local> mLocalMap;

  protected Map<PsiParameter, Param> mParamMap;

  protected PsiStatement mParentStmt;


  @Override
  public GraphNode getEntryNode() {
    return this.mEntryNode;
  }

  @Override
  public GraphNode getExitNode() {
    return this.mExitNode;
  }

  @Override
  public Graph getParentGraph() {
    return this.mParentGraph;
  }

  @Override
  public PsiStatement getParentStmt() {
    return this.mParentStmt;
  }

  @Override
  public boolean hasMultipleExit() {
    return false;
  }

  @Override
  public GraphNode[] getAllExits() {
    return GraphNode.EMPTY_ARRAY;
  }

  @Override
  public GraphNode getUnreachableNodeEntry() {
    return this.mUnreachableNode;
  }

  @Override
  public Local getLocalFromPsiLocal(PsiLocalVariable psiLocal) {
    if (mLocalMap.containsKey(psiLocal)) {
      return mLocalMap.get(psiLocal);
    }
    if (mParentGraph != null) {
      return mParentGraph.getLocalFromPsiLocal(psiLocal);
    }
    return null;
  }

  @Override
  public Local[] getAllLocals() {
    ArrayList<Local> retArray = new ArrayList<>();
    for (PsiLocalVariable psiLocal : mLocalMap.keySet()) {
      retArray.add(mLocalMap.get(psiLocal));
    }
    return retArray.toArray(Local.EMPTY_ARRAY);
  }

  @Override
  public Param getParamFromPsiParameter(PsiParameter psiParam) {
    if (mParamMap.containsKey(psiParam)) {
      return mParamMap.get(psiParam);
    }
    if (mParentGraph != null) {
      return mParentGraph.getParamFromPsiParameter(psiParam);
    }
    return null;
  }

  @Override
  public Param[] getAllParams() {
    ArrayList<Param> retArray = new ArrayList<>();
    for (PsiParameter psiParam : mParamMap.keySet()) {
      retArray.add(mParamMap.get(psiParam));
    }
    return retArray.toArray(Param.EMPTY_ARRAY);
  }

  @Override
  public void addLocal(@NotNull PsiLocalVariable psiLocal, @NotNull Local cfgLocal) {
    this.mLocalMap.put(psiLocal, cfgLocal);
  }

  @Override
  public void addParam(@NotNull PsiParameter psiParam, @NotNull Param cfgParam) {
    this.mParamMap.put(psiParam, cfgParam);
  }

  public void setParentGraph(BlockGraph parent) {
    this.mParentGraph = parent;
  }

  public void setParentStmt(PsiStatement parentStmt) {
    this.mParentStmt = parentStmt;
  }

  public BlockGraphImpl() {
    this.mEntryNode = new BlockGraphEntryNodeImpl(this);
    this.mExitNode = new BlockGraphExitNodeImpl(this);
    this.mUnreachableNode = new PlaceholderNodeImpl(this);
    this.mParentGraph = null;
    this.mLocalMap = Maps.newHashMap();
    this.mParamMap = Maps.newHashMap();
  }

}
