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
package com.android.tools.idea.editors.gfxtrace.renderers;

import com.intellij.openapi.util.Condition;
import com.intellij.util.ui.tree.WideSelectionTreeUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class AtomTreeWideSelectionTreeUI extends WideSelectionTreeUI {
  public AtomTreeWideSelectionTreeUI(final boolean wideSelection, @NotNull Condition<Integer> wideSelectionCondition) {
    super(wideSelection, wideSelectionCondition);
  }

  @Override
  protected CellRendererPane createCellRendererPane() {
    return new CellRendererPane() {
      @Override
      public void paintComponent(Graphics g, Component c, Container p, int x, int y, int w, int h, boolean shouldValidate) {
        if (c instanceof JComponent && isWideSelection()) {
          ((JComponent)c).setOpaque(false);
        }

        super.paintComponent(g, c, p, x, y, w, h, shouldValidate);
        c.setBounds(x, y, w, h); // This is the whole point of making this class -- to make the cells clickable.
      }
    };
  }
}
