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
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.Local;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.Param;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiStatement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A control flow graph For statements in \{\}
 * It should have placeholder Entry and Exit node
 */
public interface BlockGraph extends Graph {

  public GraphNode getUnreachableNodeEntry();

  /**
   * Return the statement that is
   * responsible for delaration the
   * codeblock.
   * For example. For IF statement return
   * the PsiIfStatement. For the loop statement.
   * return the PsiLoopStatement.
   *
   * @return The PsiStatment that create this graph
   */
  public PsiStatement getParentStmt();

  /**
   * Look up the Local based on the PsiLocalVariable
   * It will look up the Local in ihe parent graph.
   * Return null if not found.
   * @param psiLocal
   * @return The Local reference to the PsiLocalVariable
   */
  @Nullable
  public Local getLocalFromPsiLocal(@NotNull PsiLocalVariable psiLocal);

  /**
   * Find all Locals defined in this graph,
   * Does not include the locals in the parent
   * graph.
   * @return The array of all locals
   */
  @NotNull
  public Local[] getAllLocals();

  /**
   * Look up the Param based on the PsiParameter
   * It will look up the Params in the parent
   * graph.
   * Return null if not found.
   * @param psiParam
   * @return The Param reference to the PsiParameter
   */
  @Nullable
  public Param getParamFromPsiParameter(@NotNull PsiParameter psiParam);

  /**
   * Find all Params defined in this graph,
   * Does not include the params in the parent
   * graph.
   * @return The array of all params.
   */
  @NotNull
  public Param[] getAllParams();

  /**
   * Add the Local and PsiLocalVariable defined in this graph
   * @param psiLocal The PsiLocalVariable
   * @param cfgLocal The Local
   */
  public void addLocal(@NotNull PsiLocalVariable psiLocal, @NotNull Local cfgLocal);

  /**
   * Add the Param and PsiParameter defined in this graph
   * @param psiParam The PsiParameter
   * @param cfgParam The Param
   */
  public void addParam(@NotNull PsiParameter psiParam, @NotNull Param cfgParam);

}
