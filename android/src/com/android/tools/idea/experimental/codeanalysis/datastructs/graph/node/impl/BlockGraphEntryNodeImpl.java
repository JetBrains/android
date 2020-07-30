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
import com.android.tools.idea.experimental.codeanalysis.datastructs.graph.node.BlockGraphEntryNode;

public class BlockGraphEntryNodeImpl extends PlaceholderNodeImpl implements BlockGraphEntryNode {
  protected String mTag;

  public BlockGraphEntryNodeImpl(BlockGraph parentGraph) {
    super(parentGraph);
    mTag = "";
  }

  public void setTag(String tag) {
    this.mTag = tag;
  }

  @Override
  public String getSimpleName() {
    return String.format("BlockEntry %s ", mTag);
  }
}
