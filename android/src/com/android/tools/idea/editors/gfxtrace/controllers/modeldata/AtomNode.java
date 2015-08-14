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
package com.android.tools.idea.editors.gfxtrace.controllers.modeldata;

import com.android.tools.idea.editors.gfxtrace.service.atom.Atom;
import com.android.tools.idea.editors.gfxtrace.service.atom.AtomList;
import com.android.tools.rpclib.schema.Field;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.List;

public class AtomNode extends AtomTreeNode {
  @NotNull private static final Logger LOG = Logger.getInstance(AtomNode.class);
  private long myIndex;

  public AtomNode(long index) {
    myIndex = index;
  }

  public long getRepresentativeAtomIndex() {
    return myIndex;
  }

  @Override
  public List<TextPiece> getTextPieces(@NotNull JTree tree,
                                       @NotNull TreeNode node,
                                       @NotNull AtomList atoms) {
    Atom atom = atoms.getAtoms()[(int)myIndex];

    List<TextPiece> textPieces = new ArrayList<TextPiece>();
    textPieces.add(new TextPiece(Long.toString(myIndex) + "   ", SimpleTextAttributes.REGULAR_ATTRIBUTES));
    textPieces.add(new TextPiece(atom.getName() + "(", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES));

    for (int i = 0; i < atom.getFieldCount(); ++i) {
      if (i != 0) {
        textPieces.add(new TextPiece(", ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES));
      }

      Field field = atom.getFieldInfo(i);
      Object parameterValue = atom.getFieldValue(i);
      textPieces.add(new TextPiece(parameterValue.toString(), SimpleTextAttributes.SYNTHETIC_ATTRIBUTES));
    }

    textPieces.add(new TextPiece(")", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES));

    return textPieces;
  }
}
