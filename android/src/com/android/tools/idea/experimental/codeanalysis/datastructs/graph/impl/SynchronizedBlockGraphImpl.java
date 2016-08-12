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

import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.SynchronizedBlockGraph;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.impl.BlockGraphEntryNodeImpl;
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.impl.BlockGraphExitNodeImpl;
import com.android.tools.idea.experimental.codeanalysis.datastructs.value.Value;

public class SynchronizedBlockGraphImpl extends BlockGraphImpl implements SynchronizedBlockGraph {
  protected Value mSyncExpr;

  @Override
  public Value getSynchronizedExpression() {
    return mSyncExpr;
  }

  public void setSynchronizedExpression(Value syncExpr) {
    this.mSyncExpr = syncExpr;
  }

  public SynchronizedBlockGraphImpl() {
    super();
    if (mEntryNode instanceof BlockGraphEntryNodeImpl) {
      ((BlockGraphEntryNodeImpl)mEntryNode).setTag("[Sync]");
    }

    if (mExitNode instanceof BlockGraphExitNodeImpl) {
      ((BlockGraphExitNodeImpl)mExitNode).setTag("[Sync]");
    }
  }
}
