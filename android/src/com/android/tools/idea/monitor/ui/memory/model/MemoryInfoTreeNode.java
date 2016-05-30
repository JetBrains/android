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
package com.android.tools.idea.monitor.ui.memory.model;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * Model class that represents each row in the memory allocation table view.
 * TODO we should merge this with what's used in the current studio allocation view once we finalize on the device data structure.
 * e.g. {@link com.android.tools.idea.editors.allocations.nodes.AllocNode}
 */
public class MemoryInfoTreeNode extends DefaultMutableTreeNode {
  private String mName;
  private int mCount;

  public MemoryInfoTreeNode(String name) {
    mName = name;
  }

  public String getName() {
    return mName;
  }

  public int getCount() {
    return mCount;
  }

  public void incrementCount() {
    mCount++;
  }
}
