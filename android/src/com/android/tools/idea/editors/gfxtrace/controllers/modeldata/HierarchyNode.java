/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.tools.rpclib.rpc.AtomGroup;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class HierarchyNode {
  @NotNull private static Font treeFont = UIUtil.getTreeFont();
  @NotNull private AtomGroup myAtomGroup;

  public HierarchyNode(@NotNull AtomGroup atomGroup) {
    myAtomGroup = atomGroup;
  }

  public long getRepresentativeAtomId() {
    assert (myAtomGroup.getRange().getCount() > 0);
    return myAtomGroup.getRange().getFirst() + myAtomGroup.getRange().getCount() - 1;
  }

  public boolean isProxyFor(@NotNull AtomGroup atomGroup) {
    return myAtomGroup == atomGroup;
  }

  @NotNull
  public Component getComponent(boolean selected) {
    JBLabel label = new JBLabel(myAtomGroup.getName());
    label.setFont(treeFont);
    label.setForeground(UIUtil.getTableForeground(selected));
    return label;
  }

  @Override
  @NotNull
  public String toString() {
    return myAtomGroup.getName();
  }
}
