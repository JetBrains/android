/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.renderer;

import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.adtui.ptable.*;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.android.SdkConstants.TOOLS_URI;
import static icons.AndroidIcons.NeleIcons.DesignProperty;

public class NlTableNameRenderer extends PTableCellRenderer implements PNameRenderer {
  private static final int BEFORE_STAR_SPACING = 2;
  private static final int STAR_SIZE = 16;
  private final JPanel myPanel;
  private final JLabel myStarLabel;

  public NlTableNameRenderer() {
    myPanel = new JPanel(new BorderLayout());
    myStarLabel = new JBLabel();
    myStarLabel.setPreferredSize(new Dimension(BEFORE_STAR_SPACING + STAR_SIZE, STAR_SIZE));
    myStarLabel.setBorder(BorderFactory.createEmptyBorder(0, BEFORE_STAR_SPACING, 0, 0));
    myPanel.add(myStarLabel, BorderLayout.WEST);
    myPanel.add(this, BorderLayout.CENTER);
  }

  @Override
  public Component getTableCellRendererComponent(@NotNull JTable table, @NotNull Object value, boolean isSelected, boolean hasFocus,
                                                 int row, int column) {
    super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
    myPanel.setBackground(getBackground());
    return myPanel;
  }

  @Nullable
  private static Icon getStar(@NotNull StarState state, boolean isSelected, boolean isHovering) {
    switch (state) {
      case STARRED:
        return isSelected ? StudioIcons.LayoutEditor.Properties.FAVORITES_SELECTED : StudioIcons.LayoutEditor.Properties.FAVORITES;
      case STAR_ABLE:
        return isHovering ? StudioIcons.LayoutEditor.Properties.FAVORITES_HOVER : null;
      default:
        return null;
    }
  }

  @Override
  protected void customizeCellRenderer(@NotNull PTable table, @NotNull PTableItem item, boolean selected, boolean hasFocus,
                                       int row, int col) {
    String label = item.getParent() != null ? item.getParent().getChildLabel(item) : item.getName();
    append(label, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    setToolTipText(item.getTooltipText());

    Point hoverPos = table.getHoverPosition();
    boolean hoveringOnStar = table.isHover(row, col) && hitTestStarIcon(hoverPos.x, hoverPos.y);
    myStarLabel.setIcon(getStar(item.getStarState(), selected, hoveringOnStar));

    setIcon(item, selected, hasFocus);
    setPaintFocusBorder(false);
    setFocusBorderAroundIcon(true);
  }


  private void setIcon(PTableItem item, boolean selected, boolean hasFocus) {
    Icon groupIcon = UIUtil.getTreeNodeIcon(item.isExpanded(), selected, hasFocus);
    int beforeGroupIcon = getBeforeIconSpacing(getDepth(item), groupIcon.getIconWidth());
    int afterGroupIcon = getAfterIconSpacing(groupIcon.getIconWidth());

    Icon icon;
    int indent;
    int textGap;
    if (item.hasChildren()) {
      icon = groupIcon;
      indent = beforeGroupIcon;
      textGap = afterGroupIcon;
    }
    else {
      icon = null;
      indent = beforeGroupIcon + groupIcon.getIconWidth() + afterGroupIcon;
      textGap = 0;
    }
    if (TOOLS_URI.equals(item.getNamespace())) {
      if (icon == null) {
        icon = DesignProperty;
      }
      else {
        LayeredIcon layered = new LayeredIcon(icon, DesignProperty);
        layered.setIcon(DesignProperty, 1, afterGroupIcon + icon.getIconWidth(), 0);
        icon = layered;
      }
      textGap = 4;
    }
    super.setIcon(icon);
    setIconTextGap(textGap);
    setIpad(JBUI.insetsLeft(indent));
  }

  @Override
  public boolean hitTestStarIcon(@SwingCoordinate int x, @SwingCoordinate int y) {
    return x >= BEFORE_STAR_SPACING && x < STAR_SIZE + BEFORE_STAR_SPACING;
  }

  @Override
  public boolean hitTestTreeNodeIcon(@NotNull PTableItem item, @SwingCoordinate int x, @SwingCoordinate int y) {
    Icon icon = UIUtil.getTreeNodeIcon(item.isExpanded(), true, true);
    int beforeIcon = BEFORE_STAR_SPACING + STAR_SIZE + getBeforeIconSpacing(getDepth(item), icon.getIconWidth());
    return x >= beforeIcon && x <= beforeIcon + icon.getIconWidth();
  }

  private static int getBeforeIconSpacing(int depth, int iconWidth) {
    int nodeIndent = UIUtil.getTreeLeftChildIndent() + UIUtil.getTreeRightChildIndent();
    int leftIconOffset = Math.max(0, UIUtil.getTreeLeftChildIndent() - (iconWidth / 2));
    return nodeIndent * depth + leftIconOffset;
  }

  private static int getAfterIconSpacing(int iconWidth) {
    int nodeIndent = UIUtil.getTreeLeftChildIndent() + UIUtil.getTreeRightChildIndent();
    int leftIconOffset = Math.max(0, UIUtil.getTreeLeftChildIndent() - (iconWidth / 2));
    return Math.max(0, nodeIndent - leftIconOffset - iconWidth);
  }

  private static int getDepth(@NotNull PTableItem item) {
    int result = 0;
    while (item.getParent() != null) {
      result++;
      item = item.getParent();
      assert item != null;
    }
    return result;
  }
}
