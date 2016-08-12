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
package com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.impl;

import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.BlockGraph;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.CaseNode;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.Value;
import com.intellij.psi.PsiLiteralExpression;

public class CaseNodeImpl extends GraphNodeImpl implements CaseNode {

  protected Value mLabelValue;
  protected boolean mIsDefault;

  @Override
  public Value getLabelValue() {
    return mLabelValue;
  }

  @Override
  public void setLabelValue(Value caseLabel) {
    this.mLabelValue = caseLabel;
  }

  @Override
  public boolean isCaseDefault() {
    return this.mIsDefault;
  }

  public void setDefault() {
    this.mIsDefault = true;
  }

  public CaseNodeImpl(BlockGraph parentGraph) {
    super(parentGraph);
    this.mIsDefault = false;
  }

  @Override
  public String getSimpleName() {
    String valueStr = "";
    if (mLabelValue != null) {
      PsiLiteralExpression psiRef = (PsiLiteralExpression)mLabelValue.getPsiRef();
      if (psiRef != null) {
        valueStr = psiRef.getText();
      }
    }
    return String.format("Case %s :", valueStr);
  }
}
