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
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class AtomNode implements AtomTreeNode {
  @NotNull private static final Logger LOG = Logger.getInstance(AtomNode.class);
  private long myIndex;

  public AtomNode(long index) {
    myIndex = index;
  }

  @Override
  public long getRepresentativeAtomIndex() {
    return myIndex;
  }

  @Override
  public boolean contains(long atomImdex) {
    return atomImdex == myIndex;
  }

  @Override
  public void render(@NotNull AtomList atoms, @NotNull SimpleColoredComponent component) {
    Atom atom = atoms.getAtoms()[(int)myIndex];
    component.append(Long.toString(myIndex) + "   ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
    component.append(atom.getName() + "(", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    int resultIndex = atom.getResultIndex();
    int observationsIndex = atom.getObservationsIndex();
    boolean needComma = false;
    for (int i = 0; i < atom.getFieldCount(); ++i) {
      if (i == resultIndex || i == observationsIndex) continue;
      if (needComma) {
        component.append(", ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      }
      needComma = true;
      Field field = atom.getFieldInfo(i);
      Object parameterValue = atom.getFieldValue(i);
      field.getType().render(parameterValue, component);
    }

    component.append(")", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    if (resultIndex >= 0) {
      component.append("->", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      Field field = atom.getFieldInfo(resultIndex);
      Object parameterValue = atom.getFieldValue(resultIndex);
      field.getType().render(parameterValue, component);
    }
  }
}
