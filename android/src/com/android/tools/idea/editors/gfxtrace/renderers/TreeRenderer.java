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

import com.android.tools.idea.editors.gfxtrace.controllers.StateController;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

import static com.android.tools.idea.editors.gfxtrace.renderers.Render.render;

/**
 * This object is the renderer for AtomTreeNodes.
 * <p/>
 * Note that each atom tree needs its own TreeRenderer.
 */
public class TreeRenderer extends ColoredTreeCellRenderer {
  @Override
  public void customizeCellRenderer(@NotNull JTree tree,
                                    Object value,
                                    boolean selected,
                                    boolean expanded,
                                    boolean leaf,
                                    int row,
                                    boolean hasFocus) {
    if (value instanceof DefaultMutableTreeNode) {
      DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)value;
      render(treeNode.getUserObject(), this, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
    else if (value instanceof StateController.Node) {
      render(value, this, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    } else {
      assert false;
    }
  }
}
