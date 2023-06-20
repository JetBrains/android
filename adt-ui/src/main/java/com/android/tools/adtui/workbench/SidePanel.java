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

import com.intellij.openapi.ui.Divider;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBCardLayout;
import com.intellij.ui.JBColor;
import com.intellij.ui.SideBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.util.List;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link SidePanel} displays the currently visible docked windows
 * to one side (left or right) of the work area of a {@link WorkBench}.
 *
 * @param <T> Specifies the type of data controlled by a {@link WorkBench}.
 */
class SidePanel<T> extends JPanel implements SideModel.Listener<T> {
  private static final String SPLITTER = "SPLITTER";
  private static final String EMPTY = "EMPTY";

  private final Side mySide;
  private final JPanel myCards;
  private final CardLayout myLayout;
  private final JPanel myEmpty;
  private final Splitter mySplitter;

  SidePanel(@NotNull Side side, @NotNull SideModel<T> model) {
    super(new BorderLayout());
    mySide = side;
    mySplitter = new MySplitter();
    myEmpty = new JPanel();
    myLayout = new JBCardLayout();
    myCards = new JPanel(myLayout);
    setBorder(new SideBorder(JBColor.border(), side.isLeft() ? SideBorder.RIGHT : SideBorder.LEFT));
    add(myCards, BorderLayout.CENTER);
    model.addListener(this);
  }

  @Override
  public void modelChanged(@NotNull SideModel<T> model, @NotNull SideModel.EventType unused) {
    myCards.removeAll();
    myCards.add(mySplitter, SPLITTER);
    myCards.add(myEmpty, EMPTY);
    addVisibleTools(model.getVisibleTools(mySide));
    addHiddenTools(model.getHiddenTools(mySide));
    revalidate();
    repaint();
  }

  private void addVisibleTools(@NotNull List<AttachedToolWindow> tools) {
    mySplitter.setFirstComponent(null);
    mySplitter.setSecondComponent(null);
    setVisible(!tools.isEmpty());

    // When not used mark the splitter as disabled,
    // otherwise this splitter may be identified as the component under the mouse
    // causing the cursor to be selected based on this splitter.
    // See b/37137139
    mySplitter.setEnabled(false);

    if (tools.isEmpty()) {
      myLayout.show(myCards, EMPTY);
    }
    else if (tools.size() == 1) {
      AttachedToolWindow tool = tools.get(0);
      myCards.add(tool.getComponent(), tool.getToolName());
      myLayout.show(myCards, tool.getToolName());
    }
    else {
      AttachedToolWindow toolTop = tools.get(0);
      AttachedToolWindow toolBottom = tools.get(1);
      mySplitter.setFirstComponent(toolTop.getComponent());
      mySplitter.setSecondComponent(toolBottom.getComponent());
      toolTop.getComponent().setVisible(true);
      toolBottom.getComponent().setVisible(true);
      mySplitter.setEnabled(true);
      myLayout.show(myCards, SPLITTER);
    }
  }

  private void addHiddenTools(@NotNull List<AttachedToolWindow> tools) {
    tools.forEach(tool -> myCards.add(tool.getComponent(), tool.getToolName()));
  }

  private static class MySplitter extends Splitter {
    private MySplitter() {
      super(true);
      setDividerWidth(9);
    }

    @Override
    protected Divider createDivider() {
      Divider divider = new DividerImpl();
      divider.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP + SideBorder.BOTTOM));
      return divider;
    }
  }
}
