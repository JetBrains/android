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
import com.android.tools.idea.experimental.codeanalysis.utils.PsiCFGAnalysisUtil;
import com.google.common.collect.Maps;

import java.util.BitSet;
import java.util.Map;
import java.util.Set;

/**
 * The class hierarchy analysis class
 * Currently reserved. As it requires the class set
 * information computed from the intraprocedural data
 * flow analysis.
 */
public class CHAUtil {
  protected PsiCFGScene mScene;
  protected PsiCFGAnalysisUtil mAnalysisUtil;

  protected PsiCFGClass[] mClassIndexArray;
  protected Map<PsiCFGClass, Integer> mClassIndexMap;


  public CHAUtil(PsiCFGScene scene) {
    this.mScene = scene;
    this.mAnalysisUtil = mScene.analysisUtil;
  }

  public void buildBitSetIndex() {
    PsiCFGClass[] applicationClassArray = mScene.getAllApplicationClasses();
    PsiCFGClass[] libraryClassArray = mScene.getAllLibraryClasses();
    int numofBits = applicationClassArray.length + libraryClassArray.length;
    if (numofBits < 0) {
      throw new RuntimeException("Number of classes in this project is larger than 2G.");
    }
    mClassIndexArray = new PsiCFGClass[numofBits];
    mClassIndexMap = Maps.newHashMapWithExpectedSize(numofBits);
    int i = 0;
    for (; i < applicationClassArray.length; i++) {
      mClassIndexArray[i] = applicationClassArray[i];
      mClassIndexMap.put(applicationClassArray[i], i);
    }
    for (; i < numofBits; i++) {
      mClassIndexArray[i] = libraryClassArray[i - applicationClassArray.length];
      mClassIndexMap.put(libraryClassArray[i - applicationClassArray.length], i);
    }
  }

  public BitSet bitSetCone(PsiCFGClass clazz) {
    BitSet coneSet = new BitSet(mClassIndexArray.length);
    dfsSetConeBits(coneSet, clazz);
    return coneSet;
  }

  private void dfsSetConeBits(BitSet bitSet, PsiCFGClass clazz) {
    if (!mClassIndexMap.containsKey(clazz)) {
      throw new RuntimeException("class is not found in IndexMap: " + clazz.getQualifiedClassName());
    }
    int index = mClassIndexMap.get(clazz);
    bitSet.set(index);
    Set<PsiCFGClass> subClassSet = clazz.getSubClassSet();
    for (PsiCFGClass subClazz : subClassSet) {
      dfsSetConeBits(bitSet, subClazz);
    }
  }

}
