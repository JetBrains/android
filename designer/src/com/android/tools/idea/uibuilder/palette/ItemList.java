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
package com.android.tools.idea.uibuilder.palette;

import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.common.ColoredIconGenerator;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ExpandableItemsHandler;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import icons.StudioIcons;
import java.awt.event.MouseEvent;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * A list control for displaying palette items.
 * <p>
 * +------------------------+
 * | ⌸ Button               |
 * | ⍄ ToggleButton         |
 * | ⍓ FloatingActionB... ↓ |
 * |   ...                  |
 * +------------------------+
 * Example of an item list.
 * <p>
 * The item names each have an icon to the left that represent the item.
 * An item may also have a download icon to the right of the name indicating
 * that this item requires a project dependency to be added.
 * If an item name is wider than the allotted width, it will be truncated and
 * shown with ellipsis.
 */
public class ItemList extends ListWithMargin<Palette.Item> {
  private final DependencyManager myDependencyManager;

  public ItemList(@NotNull DependencyManager dependencyManager) {
    myDependencyManager = dependencyManager;
    setCellRenderer(new ItemCellRenderer());
  }

  public void setEmptyText(@NotNull Pair<String, String> text) {
    StatusText status = getEmptyText();
    status.clear();
    status.appendText(text.first);
    status.appendSecondaryText(text.second, StatusText.DEFAULT_ATTRIBUTES, null);
  }

  @Override
  protected int getRightMarginWidth() {
    return StudioIcons.LayoutEditor.Extras.PALETTE_DOWNLOAD.getIconWidth();
  }

  private boolean displayFittedTextIfNecessary(int index) {
    return !UIUtil.isClientPropertyTrue(this, ExpandableItemsHandler.EXPANDED_RENDERER) &&
           !getExpandableItemsHandler().getExpandedItems().contains(index);
  }

  private boolean displayDownloadIcon(@NotNull Palette.Item item, int index) {
    return myDependencyManager.needsLibraryLoad(item) &&
           displayFittedTextIfNecessary(index);
  }

  private static class ItemCellRenderer implements ListCellRenderer<Palette.Item> {
    private final ItemPanelRenderer myPanel;
    private final JBLabel myDownloadIcon;
    private final TextCellRenderer myTextRenderer;

    private ItemCellRenderer() {
      myPanel = new ItemPanelRenderer();
      myDownloadIcon = new JBLabel();
      myTextRenderer = new TextCellRenderer();
      myPanel.add(myTextRenderer, BorderLayout.CENTER);
      myPanel.add(myDownloadIcon, BorderLayout.EAST);
    }

    @NotNull
    @Override
    public Component getListCellRendererComponent(@NotNull JList<? extends Palette.Item> list,
                                                  @NotNull Palette.Item item,
                                                  int index,
                                                  boolean selected,
                                                  boolean hasFocus) {
      myTextRenderer.getListCellRendererComponent(list, item, index, selected, hasFocus);
      myPanel.setBackground(selected ? UIUtil.getTreeSelectionBackground(hasFocus) : null);
      myPanel.setForeground(UIUtil.getTreeForeground(selected, hasFocus));
      myPanel.setBorder(JBUI.Borders.empty(0, 3));
      myPanel.setListSize(list.getWidth(), list.getHeight());

      ItemList itemList = (ItemList)list;
      myDownloadIcon.setVisible(itemList.displayDownloadIcon(item, index));
      myDownloadIcon.setIcon(selected && hasFocus ? StudioIcons.LayoutEditor.Extras.PALETTE_DOWNLOAD_SELECTED
                                                  : StudioIcons.LayoutEditor.Extras.PALETTE_DOWNLOAD);
      myDownloadIcon.setToolTipText("Add Project Dependency");
      myPanel.setToolTipText(item.getTitle());
      return myPanel;
    }
  }

  private static class TextCellRenderer extends ColoredListCellRenderer<Palette.Item> {

    @Override
    protected void customizeCellRenderer(@NotNull JList<? extends Palette.Item> list,
                                         @NotNull Palette.Item item,
                                         int index,
                                         boolean selected,
                                         boolean hasFocus) {
      ItemList itemList = (ItemList)list;
      Icon icon = item.getIcon();
      String text = item.getTitle();

      if (itemList.displayFittedTextIfNecessary(index)) {
        int leftMargin = icon.getIconWidth() + myIconTextGap + getIpad().right + getIpad().left;
        int rightMargin = StudioIcons.LayoutEditor.Extras.PALETTE_DOWNLOAD.getIconWidth();
        text = AdtUiUtils
          .shrinkToFit(text, s -> list.getFontMetrics(list.getFont()).stringWidth(s) <= list.getWidth() - leftMargin - rightMargin);
      }

      setBackground(selected ? UIUtil.getTreeSelectionBackground(hasFocus) : null);
      mySelectionForeground = UIUtil.getTreeForeground(selected, hasFocus);
      setIcon(selected && hasFocus ? ColoredIconGenerator.INSTANCE.generateWhiteIcon(icon) : icon);
      append(text);
    }
  }

  private static class ItemPanelRenderer extends JPanel {
    private int myWidth;
    private int myHeight;

    private ItemPanelRenderer() {
      super(new BorderLayout());
    }

    private void setListSize(int width, int height) {
      myWidth = width;
      myHeight = height;
    }

    @Override
    public String getToolTipText(MouseEvent event) {
      setSize(myWidth, myHeight);
      doLayout();
      Rectangle bounds = new Rectangle();
      for (int index=0; index < getComponentCount(); index++) {
        JComponent component = (JComponent)getComponent(index);
        component.getBounds(bounds);
        if (bounds.contains(event.getX(), event.getY())) {
          return component.getToolTipText();
        }
      }
      return getToolTipText();
    }
  }
}
