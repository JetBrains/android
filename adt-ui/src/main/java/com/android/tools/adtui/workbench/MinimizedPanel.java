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
import com.intellij.ui.JBColor;
import com.intellij.ui.SideBorder;
import java.awt.Component;
import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

/**
 * The {@link MinimizedPanel} shows tool button to the left or right of the {@link WorkBench}.
 *
 * @param <T> Specifies the type of data controlled by the {@link WorkBench}.
 */
class MinimizedPanel<T> extends JPanel implements SideModel.Listener<T> {
  private final SideModel<T> myModel;
  private final Side mySide;
  private final Component myFiller;
  private boolean myHasVisibleButtons;

  MinimizedPanel(@NotNull Side side, @NotNull SideModel<T> model) {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(IdeBorderFactory.createBorder(side.isLeft() ? SideBorder.RIGHT : SideBorder.LEFT));
    setBackground(JBColor.white);
    setOpaque(false);
    myModel = model;
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
    button.setVisible(true);
    myHasVisibleButtons |= tool.isMinimized() || tool.isAutoHide();
    add(button);
  }

  public int drag(@NotNull AttachedToolWindow<T> tool, int position) {
    AbstractButton button = tool.getMinimizedButton();
    if (!isOpaque()) {
      enableMinimizeButtonDragAndDrop(true);
      button.setVisible(true);
    }
    int insertIndex = findInsertIndex(position, button);
    int index = getComponentIndex(button);
    if (index == insertIndex) {
      return insertIndex;
    }
    if (index >= 0) {
      remove(index);
    }
    add(button, insertIndex);
    revalidate();
    repaint();
    return insertIndex;
  }

  public void dragExit(@NotNull AttachedToolWindow<T> tool) {
    tool.getMinimizedButton().setVisible(false);
    enableMinimizeButtonDragAndDrop(false);
    revalidate();
    repaint();
  }

  public void dragDrop(@NotNull AttachedToolWindow<T> tool, int position) {
    int index = drag(tool, position);
    tool.getMinimizedButton().setVisible(false);
    int fillerIndex = getComponentIndex(myFiller);
    Split newSplit = index > fillerIndex ? Split.BOTTOM : Split.TOP;
    int toolIndex = newSplit.isBottom() ? index - fillerIndex - 1 : index;
    myModel.changeToolSettingsAfterDragAndDrop(tool, mySide, newSplit, toolIndex);
    enableMinimizeButtonDragAndDrop(false);
  }

  private void enableMinimizeButtonDragAndDrop(boolean enable) {
    setOpaque(enable);
    setVisible(enable || myHasVisibleButtons);
    revalidate();
    repaint();
  }

  private int findInsertIndex(int position, @NotNull Component draggedButton) {
    int index = 0;
    int y = 0;
    int extraFillerHeight = draggedButton.getParent() == this ? draggedButton.getHeight() : 0;
    for (Component component : getComponents()) {
      if (component != draggedButton) {
        if (component instanceof Box.Filler) {
          if (position < y + (component.getHeight() + extraFillerHeight) / 2) {
            return index;
          }
          index++;
          y += extraFillerHeight;
        }
        y += component.getHeight();
        if (position < y) {
          return index;
        }
        index++;
      }
    }
    return index - 1;
  }

  private int getComponentIndex(@NotNull Component component) {
    for (int index = 0; index < getComponentCount(); index++) {
      if (getComponent(index) == component) {
        return index;
      }
    }
    return -1;
  }
}
