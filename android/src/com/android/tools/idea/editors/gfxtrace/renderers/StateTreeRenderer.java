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
package com.android.tools.idea.editors.gfxtrace.renderers;

import com.android.tools.idea.editors.gfxtrace.controllers.modeldata.StateTreeNode;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class StateTreeRenderer extends ColoredTreeCellRenderer {
  @Override
  public void customizeCellRenderer(@NotNull JTree tree,
                                    Object value,
                                    boolean selected,
                                    boolean expanded,
                                    boolean leaf,
                                    int row,
                                    boolean hasFocus) {
    assert (value instanceof StateTreeNode);
    StateTreeNode node = (StateTreeNode)value;
    if (node.getValue() != null) {
      append(node.getName() + ": ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
      append(node.getValueString(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    }
    else {
      if (node.hasChildren()) {
        append(node.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
      else {
        append(node.getName(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
    }
  }
}
