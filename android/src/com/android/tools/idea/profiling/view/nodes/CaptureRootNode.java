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
package com.android.tools.idea.profiling.view.nodes;

import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.containers.SortedList;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;

public class CaptureRootNode extends SimpleNode {

  @NotNull
  private final List<CaptureTypeNode> myTypes;

  public CaptureRootNode() {
    myTypes = new SortedList<CaptureTypeNode>(new Comparator<CaptureTypeNode>() {
      @Override
      public int compare(CaptureTypeNode a, CaptureTypeNode b) {
        return a.getName().compareToIgnoreCase(b.getName());
      }
    });
  }


  @Override
  public SimpleNode[] getChildren() {
    return myTypes.toArray(new CaptureTypeNode[myTypes.size()]);
  }

  public void clear() {
    myTypes.clear();
  }

  public void addType(@NotNull CaptureTypeNode type) {
    myTypes.add(type);
  }
}
