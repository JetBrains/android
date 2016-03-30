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
package com.android.tools.idea.gradle.structure.configurables.ui;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

import static com.intellij.util.ui.UIUtil.getTreeCollapsedIcon;
import static com.intellij.util.ui.UIUtil.getTreeExpandedIcon;
import static javax.swing.BorderFactory.createEmptyBorder;

public class CollapsiblePanel extends JPanel {

  @NotNull private final JPanel myPanel;
  @NotNull private final JCheckBox myExpandControl;

  private JComponent myContents;
  private boolean myExpanded;

  public CollapsiblePanel() {
    this("");
  }

  public CollapsiblePanel(@NotNull String title) {
    super(new BorderLayout());
    Icon treeExpandedIcon = getTreeExpandedIcon();
    myPanel = new JPanel(new BorderLayout());
    add(myPanel, BorderLayout.CENTER);


    myExpandControl = new JCheckBox(title);
    Font font = myExpandControl.getFont();
    myExpandControl.setFont(font.deriveFont(Font.BOLD));
    int left = 4;
    myExpandControl.setBorder(createEmptyBorder(0, left, 0, 0));
    myExpandControl.setHorizontalTextPosition(SwingConstants.RIGHT);
    myExpandControl.setIcon(getTreeCollapsedIcon());
    myExpandControl.setSelectedIcon(treeExpandedIcon);
    myExpandControl.addChangeListener(new CollapseListener());
    add(myExpandControl, BorderLayout.NORTH);

    myPanel.setBorder(createEmptyBorder(10, 10 + treeExpandedIcon.getIconWidth() + myExpandControl.getIconTextGap() + left, 10, 10));

    setExpanded(true);
  }

  public void setContents(@NotNull JComponent contents) {
    if (myContents != null) {
      myPanel.remove(myContents);
    }
    myContents = contents;
    myPanel.add(myContents, BorderLayout.CENTER);
    revalidate();
    repaint();
  }

  @NotNull
  public String getTitle() {
    return myExpandControl.getText();
  }

  public void setTitle(@NotNull String title) {
    myExpandControl.setText(title);
  }

  @Override
  public String getToolTipText() {
    return myExpandControl.getToolTipText();
  }

  @Override
  public void setToolTipText(String toolTipText) {
    myExpandControl.setToolTipText(toolTipText);
  }

  public boolean isExpanded() {
    return myExpanded;
  }

  public void setExpanded(boolean expanded) {
    boolean oldExpanded = myExpanded;
    if (oldExpanded != expanded) {
      myExpanded = expanded;
      myExpandControl.setSelected(myExpanded);
      if (myExpanded) {
        add(myPanel, BorderLayout.CENTER);
      }
      else {
        remove(myPanel);
      }
      revalidate();
      repaint();
      firePropertyChange("expanded", oldExpanded, expanded);
    }
  }

  private class CollapseListener implements ChangeListener {
    @Override
    public void stateChanged(ChangeEvent event) {
      setExpanded(myExpandControl.isSelected());
    }
  }
}
