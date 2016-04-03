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

import com.intellij.ui.SimpleColoredComponent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

import static com.intellij.ui.SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
import static com.intellij.util.ui.UIUtil.getTreeCollapsedIcon;
import static com.intellij.util.ui.UIUtil.getTreeExpandedIcon;
import static javax.swing.BorderFactory.createEmptyBorder;

public class CollapsiblePanel extends JPanel {
  @NotNull private final JPanel myPanel;
  @NotNull private final JCheckBox myExpandButton;
  @NotNull private final SimpleColoredComponent myTitleComponent;

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

    myExpandButton = new JCheckBox(" ");
    myExpandButton.setFocusable(false);
    myExpandButton.setIcon(getTreeCollapsedIcon());
    myExpandButton.setSelectedIcon(treeExpandedIcon);
    myExpandButton.addChangeListener(new CollapseListener());

    myTitleComponent = new SimpleColoredComponent();
    myTitleComponent.append(title, REGULAR_BOLD_ATTRIBUTES);
    int iconTextGap = 5;
    myTitleComponent.setBorder(createEmptyBorder(0, iconTextGap, 0, 0));

    JPanel expandPanel = new JPanel(new BorderLayout());
    expandPanel.add(myExpandButton, BorderLayout.WEST);
    expandPanel.add(myTitleComponent, BorderLayout.CENTER);

    int left = 4;
    expandPanel.setBorder(createEmptyBorder(0, left, 0, 0));
    add(expandPanel, BorderLayout.NORTH);

    myPanel.setBorder(createEmptyBorder(10, 10 + treeExpandedIcon.getIconWidth() + left + iconTextGap, 10, 10));

    setExpanded(true);
  }

  public void setContents(@NotNull JComponent contents) {
    if (myContents != null) {
      myPanel.remove(myContents);
    }
    myContents = contents;
    myPanel.add(myContents, BorderLayout.CENTER);
    revalidateAndRepaint();
  }

  @NotNull
  public SimpleColoredComponent getTitleComponent() {
    return myTitleComponent;
  }

  @Override
  public String getToolTipText() {
    return myExpandButton.getToolTipText();
  }

  @Override
  public void setToolTipText(String toolTipText) {
    myExpandButton.setToolTipText(toolTipText);
  }

  public boolean isExpanded() {
    return myExpanded;
  }

  public void setExpanded(boolean expanded) {
    boolean oldExpanded = myExpanded;
    if (oldExpanded != expanded) {
      myExpanded = expanded;
      myExpandButton.setSelected(myExpanded);
      if (myExpanded) {
        add(myPanel, BorderLayout.CENTER);
      }
      else {
        remove(myPanel);
      }
      revalidateAndRepaint();
      firePropertyChange("expanded", oldExpanded, expanded);
    }
  }

  private void revalidateAndRepaint() {
    revalidate();
    repaint();
  }

  private class CollapseListener implements ChangeListener {
    @Override
    public void stateChanged(ChangeEvent event) {
      setExpanded(myExpandButton.isSelected());
    }
  }
}
