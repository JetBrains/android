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

import com.android.tools.idea.editors.gfxtrace.controllers.modeldata.AtomTreeNode;
import com.android.tools.idea.editors.gfxtrace.controllers.modeldata.EnumInfoCache;
import com.android.tools.idea.editors.gfxtrace.schema.AtomReader;
import com.intellij.ui.ColoredTreeCellRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.List;

/**
 * This object is the renderer for AtomTreeNodes.
 * <p/>
 * Note that each atom tree needs its own AtomTreeRenderer.
 */
public class AtomTreeRenderer extends ColoredTreeCellRenderer {
  private EnumInfoCache myEnumInfoCache;
  private AtomReader myAtomReader;

  public AtomTreeRenderer() {
  }

  public void init(@NotNull EnumInfoCache enumInfoCache, @NotNull AtomReader atomReader) {
    myEnumInfoCache = enumInfoCache;
    myAtomReader = atomReader;
  }

  public void clearState() {
    myEnumInfoCache = null;
    myAtomReader = null;
  }

  @Override
  public void customizeCellRenderer(@NotNull JTree tree,
                                    Object value,
                                    boolean selected,
                                    boolean expanded,
                                    boolean leaf,
                                    int row,
                                    boolean hasFocus) {
    assert (value != null && value instanceof DefaultMutableTreeNode);
    DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)value;
    Object userObject = treeNode.getUserObject();

    assert (userObject instanceof AtomTreeNode);
    List<AtomTreeNode.TextPiece> textPieceList = ((AtomTreeNode)userObject).getTextPieces(tree, treeNode, myEnumInfoCache, myAtomReader);
    for (AtomTreeNode.TextPiece textPiece : textPieceList) {
      append(textPiece.myString, textPiece.myTextAttributes);
    }
  }
}
