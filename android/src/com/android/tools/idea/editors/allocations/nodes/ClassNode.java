/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.allocations.nodes;

import com.android.ddmlib.AllocationInfo;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class ClassNode extends PackageNode {
  @NotNull
  private Map<String, AllocNode> mySimilarObjectsMap = new HashMap<>();

  public ClassNode(@NotNull String name) {
    super(name);
  }

  @Override
  protected void addChild(@NotNull AbstractTreeNode node) {
    assert node instanceof AllocNode;
    AllocationInfo allocInfo = ((AllocNode)node).getAllocation();
    String key = String.format("%s,%s", allocInfo.getAllocatedClass(), allocInfo.getSize());
    if (mySimilarObjectsMap.containsKey(key)) {
      mySimilarObjectsMap.get(key).incrementCount();
    }
    else {
      super.addChild(node);
      mySimilarObjectsMap.put(key, (AllocNode)node);
    }
  }
}
