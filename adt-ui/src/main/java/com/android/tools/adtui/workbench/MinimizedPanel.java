/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.adtui.workbench;

import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * The {@link MinimizedPanel} shows tool button to the left or right of the {@link WorkBench}.
 *
 * @param <T> Specifies the type of data controlled by the {@link WorkBench}.
 */
class MinimizedPanel<T> extends JPanel implements SideModel.Listener<T> {
  private final Side mySide;
  private final Component myFiller;
  private boolean myHasVisibleButtons;

  public MinimizedPanel(@NotNull Side side, @NotNull SideModel<T> model) {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(IdeBorderFactory.createBorder(side.isLeft() ? SideBorder.RIGHT : SideBorder.LEFT));
    mySide = side;
    myFiller = Box.createVerticalGlue();
    model.addListener(this);
  }

  @Override
  public void modelChanged(@NotNull SideModel<T> model, @NotNull SideModel.EventType unused) {
    removeAll();
    myHasVisibleButtons = false;
    model.getTopTools(mySide).forEach(this::addButton);
    add(myFiller);
    model.getBottomTools(mySide).forEach(this::addButton);
    setVisible(myHasVisibleButtons);
    revalidate();
    repaint();
  }

  private void addButton(@NotNull AttachedToolWindow tool) {
    AbstractButton button = tool.getMinimizedButton();
    boolean visible = tool.isMinimized() || tool.isAutoHide();
    button.setVisible(visible);
    myHasVisibleButtons |= visible;
    add(button);
  }
}
