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
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiStatement;

/**
 * A control flow graph For statements in \{\}
 * It should have dummy Entry and Exit node
 * Created by haowei on 6/10/16.
 */
public interface BlockGraph extends Graph {

  public GraphNode getUnreachableNodeEntry();

  /**
   * Get all declared local variables
   * Will not return null even no local is declared.
   *
   * @return
   */
  public Local[] getDeclaredLocals();

  /**
   * Return null if not found
   *
   * @param psiLocal
   * @return
   */
  public Local getLocalFromPsiLocal(PsiLocalVariable psiLocal);

  /**
   * Return the statement that is
   * responsible for delaration the
   * codeblock.
   * For example. For IF statement return
   * the PsiIfStatement. For the loop statement.
   * return the PsiLoopStatement.
   *
   * @return
   */
  public PsiStatement getParentStmt();

}
