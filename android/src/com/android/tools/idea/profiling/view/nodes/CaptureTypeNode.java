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

import com.android.tools.idea.profiling.capture.CaptureType;
import com.intellij.icons.AllIcons;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.containers.SortedList;

import java.util.Comparator;
import java.util.List;

public class CaptureTypeNode extends SimpleNode {
  private final CaptureType myType;
  private final List<CaptureNode> myCaptureNodes;

  public CaptureTypeNode(CaptureType type) {
    myType = type;
    myCaptureNodes = new SortedList<CaptureNode>(new Comparator<CaptureNode>() {
      @Override
      public int compare(CaptureNode a, CaptureNode b) {
        return a.getName().compareToIgnoreCase(b.getName());
      }
    });

    getTemplatePresentation().addText(type.getName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    setIcon(AllIcons.Modules.SourceFolder);
  }

  @Override
  public SimpleNode[] getChildren() {
    return myCaptureNodes.toArray(new SimpleNode[myCaptureNodes.size()]);
  }

  public void addCapture(CaptureNode captureNode) {
    myCaptureNodes.add(captureNode);
  }

  public void clear() {
    myCaptureNodes.clear();
  }
}
