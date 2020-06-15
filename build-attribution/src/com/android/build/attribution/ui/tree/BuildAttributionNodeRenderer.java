/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.build.attribution.ui.tree;

import static com.intellij.ui.SimpleTextAttributes.GRAYED_ATTRIBUTES;
import static com.intellij.util.ui.UIUtil.getTreeSelectionForeground;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.ui.RelativeFont;
import com.intellij.ui.tree.ui.DefaultTreeUI;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Shape;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import org.jetbrains.annotations.NotNull;

/**
 * Class to allow rendering text to the right of the tree nodes.
 * Copied from BuildTreeConsoleView
 */
public class BuildAttributionNodeRenderer extends NodeRenderer {
  {
    putClientProperty(DefaultTreeUI.SHRINK_LONG_RENDERER, true);
  }

  private String myDurationText;
  private Color myDurationColor;
  private int myDurationWidth;
  private int myDurationOffset;

  @Override
  public void customizeCellRenderer(@NotNull JTree tree,
                                    Object value,
                                    boolean selected,
                                    boolean expanded,
                                    boolean leaf,
                                    int row,
                                    boolean hasFocus) {
    super.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus);
    myDurationText = null;
    myDurationColor = null;
    myDurationWidth = 0;
    myDurationOffset = 0;
    final DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
    final Object userObj = node.getUserObject();
    if (userObj instanceof AbstractBuildAttributionNode) {
      myDurationText = ((AbstractBuildAttributionNode)userObj).getTimeSuffix();
      if (myDurationText != null) {
        FontMetrics metrics = getFontMetrics(RelativeFont.SMALL.derive(getFont()));
        myDurationWidth = metrics.stringWidth(myDurationText);
        myDurationOffset = metrics.getHeight() / 2; // an empty area before and after the text
        myDurationColor = selected ? getTreeSelectionForeground(hasFocus) : GRAYED_ATTRIBUTES.getFgColor();
      }
    }
  }

  @Override
  protected void paintComponent(Graphics g) {
    UISettings.setupAntialiasing(g);
    Shape clip = null;
    int width = getWidth();
    int height = getHeight();
    if (isOpaque()) {
      // paint background for expanded row
      g.setColor(getBackground());
      g.fillRect(0, 0, width, height);
    }
    if (myDurationWidth > 0) {
      width -= myDurationWidth + myDurationOffset;
      if (width > 0 && height > 0) {
        g.setColor(myDurationColor);
        g.setFont(RelativeFont.SMALL.derive(getFont()));
        g.drawString(myDurationText, width + myDurationOffset / 2, getTextBaseLine(g.getFontMetrics(), height));
        clip = g.getClip();
        g.clipRect(0, 0, width, height);
      }
    }

    super.paintComponent(g);
    // restore clip area if needed
    if (clip != null) g.setClip(clip);
  }
}
