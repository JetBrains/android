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

import com.android.tools.idea.editors.gfxtrace.rpc.ParameterInfo;
import com.android.tools.idea.editors.gfxtrace.schema.Atom;
import com.android.tools.idea.editors.gfxtrace.schema.AtomReader;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AtomNode extends AtomTreeNode {
  @NotNull private static final Logger LOG = Logger.getInstance(AtomNode.class);
  private long myId;

  public AtomNode(long id) {
    myId = id;
  }

  public long getRepresentativeAtomId() {
    return myId;
  }

  @Override
  public List<TextPiece> getTextPieces(@NotNull JTree tree,
                                       @NotNull TreeNode node,
                                       @NotNull EnumInfoCache enumInfoCache,
                                       @NotNull AtomReader atomReader) {
    Atom atom;
    try {
      atom = atomReader.read(myId);
    }
    catch (IOException e) {
      LOG.error(e);
      return Collections.emptyList();
    }

    List<TextPiece> textPieces = new ArrayList<TextPiece>();
    textPieces.add(new TextPiece(Long.toString(myId) + "   ", SimpleTextAttributes.REGULAR_ATTRIBUTES));
    textPieces.add(new TextPiece(atom.info.getName() + "(", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES));

    for (int i = 0; i < atom.info.getParameters().length; ++i) {
      if (i != 0) {
        textPieces.add(new TextPiece(", ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES));
      }

      ParameterInfo parameterInfo = atom.info.getParameters()[i];
      Object parameterValue = atom.parameters[i].value;

      switch (parameterInfo.getType().getKind()) {
        case Enum:
          textPieces.add(new TextPiece(parameterValue.toString(), SimpleTextAttributes.SYNTHETIC_ATTRIBUTES));
          break;

        default:
          textPieces.add(new TextPiece(atom.parameters[i].value.toString(), SimpleTextAttributes.SYNTHETIC_ATTRIBUTES));
          break;
      }
    }

    textPieces.add(new TextPiece(")", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES));

    return textPieces;
  }
}
