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

import com.android.tools.idea.editors.gfxtrace.service.atom.AtomGroup;
import com.android.tools.idea.editors.gfxtrace.service.atom.AtomList;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.util.Collections;
import java.util.List;

public class HierarchyNode implements AtomTreeNode {
  @NotNull private AtomGroup myAtomGroup;

  public HierarchyNode(@NotNull AtomGroup atomGroup) {
    myAtomGroup = atomGroup;
  }

  @Override
  public long getRepresentativeAtomIndex() {
    assert (myAtomGroup.isValid());
    return myAtomGroup.getRange().getEnd() - 1;
  }

  @Override
  public boolean contains(long atomImdex) {
    return myAtomGroup.getRange().contains(atomImdex);
  }

  @Override
  public void render(@NotNull AtomList atoms, @NotNull SimpleColoredComponent component) {
    component.append(myAtomGroup.getName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
  }
}
