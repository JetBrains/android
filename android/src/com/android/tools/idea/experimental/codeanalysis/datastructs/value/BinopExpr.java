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
package com.android.tools.idea.experimental.codeanalysis.datastructs.value;

import com.intellij.psi.tree.IElementType;

public interface BinopExpr extends Expr {
  public Value getOp1();

  public Value getOp2();

  public void setOp1(Value op1);

  public void setOp2(Value op2);

  public IElementType getOperator();

  public void setOperator(IElementType operator);
}
