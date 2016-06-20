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

import com.android.tools.idea.experimental.codeanalysis.datastructs.PsiCFGMethod;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.MethodGraph;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.impl.BlockGraphEntryNodeImpl;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.impl.BlockGraphExitNodeImpl;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.Param;
import com.google.common.collect.Maps;
import com.intellij.psi.PsiParameter;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Created by haowei on 6/14/16.
 */
public class MethodGraphImpl extends BlockGraphImpl implements MethodGraph {

  private Map<PsiParameter, Param> mParamMap;
  private PsiCFGMethod mCFGMethod;

  @NotNull
  @Override
  public Param[] getParams() {
    Param[] retArray = new Param[mParamMap.size()];
    int i = 0;
    for (PsiParameter curPsiRef : this.mParamMap.keySet()) {
      Param curParam = this.mParamMap.get(curPsiRef);
      retArray[i] = curParam;
      i++;
    }
    return retArray;
  }

  @Override
  public PsiCFGMethod getPsiCFGMethod() {
    return this.mCFGMethod;
  }

  @Override
  public Param getParamFromPsiParam(PsiParameter psiParameter) {
    if (mParamMap.containsKey(psiParameter)) {
      return this.mParamMap.get(psiParameter);
    }
    return null;
  }

  public void addParam(Param param) {
    mParamMap.put((PsiParameter)param.getPsiRef(), param);
  }

  public MethodGraphImpl(@NotNull PsiCFGMethod methodRef) {
    super();
    mParamMap = Maps.newHashMap();
    this.mCFGMethod = methodRef;
    if (mEntryNode instanceof BlockGraphEntryNodeImpl) {
      ((BlockGraphEntryNodeImpl)mEntryNode).setTag("[Method]");
    }

    if (mExitNode instanceof BlockGraphExitNodeImpl) {
      ((BlockGraphExitNodeImpl)mExitNode).setTag("[Method]");
    }
  }
}
