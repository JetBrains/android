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

import com.intellij.icons.AllIcons;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static com.android.tools.idea.gradle.structure.configurables.ui.UiUtil.revalidateAndRepaint;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
import static javax.swing.BorderFactory.createEmptyBorder;

public class CollapsiblePanel extends JPanel {
  @NotNull private final JPanel myPanel;
  @NotNull private final JBLabel myExpandButton;
  @NotNull private final SimpleColoredComponent myTitleComponent;
  @NotNull private final Icon myExpandedIcon;
  @NotNull private final Icon myCollapsedIcon;

  private JComponent myContents;
  private boolean myExpanded;

  public CollapsiblePanel() {
    this("");
  }

  public CollapsiblePanel(@NotNull String title) {
    super(new BorderLayout());

    myPanel = new JPanel(new BorderLayout());
    add(myPanel, BorderLayout.CENTER);

    myExpandButton = new JBLabel(" ");
    myExpandButton.setFocusable(false);
    myExpandButton.addMouseListener(new CollapseListener());

    myExpandedIcon = AllIcons.Nodes.TreeDownArrow;
    myCollapsedIcon = AllIcons.Nodes.TreeRightArrow;

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

    myPanel.setBorder(createEmptyBorder(10, 10 + myExpandedIcon.getIconWidth() + left + iconTextGap, 10, 10));

    setExpanded(true);
  }

  public void setContents(@NotNull JComponent contents) {
    if (myContents != null) {
      myPanel.remove(myContents);
    }
    myContents = contents;
    myPanel.add(myContents, BorderLayout.CENTER);
    revalidateAndRepaint(this);
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
      Icon icon = myExpanded ? myExpandedIcon : myCollapsedIcon;
      myExpandButton.setIcon(icon);
      if (myExpanded) {
        add(myPanel, BorderLayout.CENTER);
      }
      else {
        remove(myPanel);
      }
      revalidateAndRepaint(this);
      firePropertyChange("expanded", oldExpanded, expanded);
    }
  }

  private class CollapseListener extends MouseAdapter {
    @Override
    public void mousePressed(MouseEvent e) {
      setExpanded(!myExpanded);
    }
  }
}
