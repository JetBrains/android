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

import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * A list control for displaying palette categories.
 *
 *      +---------------+      +----------------+
 *      | Common        |      | All Results  25|
 *      | Widgets       |      | Widgets       2|
 *      | Long Catego...|      | Long Cate...  3|
 *      | ...           |      | ...            |
 *      +---------------+      +----------------+
 *      Example of normal      Example of search
 *      category list.         result with numbers.
 *
 * The categories list is either a normal category list with just category names,
 * or a search result which also has the match counts in the right side for each
 * category.
 *
 * If a category name is wider than the alotted width, it will be truncated and
 * shown with ellipsis.
 */
public class CategoryList extends ListWithMargin<Palette.Group> {
  // We are making the assumption that the match counts will not be larger than 2 digits.
  // This determines the space reserved for match counts in a search result.
  private static final String MATCH_COUNT_PATTERN = "55";

  private final int myMatchCountMargin;

  public CategoryList() {
    myMatchCountMargin = getFontMetrics(getFont()).stringWidth(MATCH_COUNT_PATTERN);
    setCellRenderer(new CategoryCellRenderer());
  }

  @Override
  protected int getRightMarginWidth() {
    return hasMatchCounts() ? myMatchCountMargin : 0;
  }

  private boolean displayMatchCounts() {
    CategoryListModel categoryModel = (CategoryListModel)getModel();
    assert categoryModel != null;
    return categoryModel.hasMatchCounts();
  }

  private int getMatchCountAt(int index) {
    CategoryListModel categoryModel = (CategoryListModel)getModel();
    assert categoryModel != null;
    return categoryModel.getMatchCountAt(index);
  }

  private boolean hasMatchCounts() {
    CategoryListModel categoryModel = (CategoryListModel)getModel();
    assert categoryModel != null;
    return categoryModel.hasMatchCounts();
  }

  private static class CategoryCellRenderer implements ListCellRenderer<Palette.Group> {
    private final JPanel myPanel;
    private final TextCellRenderer myTextRenderer;
    private final JLabel myMatchCount;

    private CategoryCellRenderer() {
      myMatchCount = new JBLabel();
      myTextRenderer = new TextCellRenderer();
      myPanel = new JPanel(new BorderLayout());
      myPanel.add(myTextRenderer, BorderLayout.CENTER);
      myPanel.add(myMatchCount, BorderLayout.EAST);
    }

    @NotNull
    @Override
    public Component getListCellRendererComponent(@NotNull JList<? extends Palette.Group> list,
                                                  @NotNull Palette.Group group,
                                                  int index,
                                                  boolean selected,
                                                  boolean hasFocus) {
      myTextRenderer.getListCellRendererComponent(list, group, index, selected, hasFocus);
      myPanel.setBackground(selected ? UIUtil.getTreeSelectionBackground(hasFocus) : null);
      myPanel.setForeground(UIUtil.getTreeForeground(selected, hasFocus));
      myPanel.setBorder(JBUI.Borders.empty(2, 3));

      CategoryList categoryList = (CategoryList)list;
      myMatchCount.setText(String.valueOf(categoryList.getMatchCountAt(index)));
      myMatchCount.setVisible(categoryList.displayMatchCounts());
      myMatchCount.setForeground(hasFocus ? UIUtil.getTreeSelectionForeground(true) : JBColor.GRAY);

      myPanel.setToolTipText(group.getName());
      return myPanel;
    }
  }

  private static class TextCellRenderer extends ColoredListCellRenderer<Palette.Group> {
    private static final SimpleTextAttributes SMALL_FONT = new SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER, null);

    @Override
    protected void customizeCellRenderer(@NotNull JList<? extends Palette.Group> list,
                                         @NotNull Palette.Group group,
                                         int index,
                                         boolean selected,
                                         boolean hasFocus) {
      String text = group.getName();
      setBackground(selected ? UIUtil.getTreeSelectionBackground(hasFocus) : null);
      mySelectionForeground = UIUtil.getTreeForeground(selected, hasFocus);
      append(text, SMALL_FONT);
    }
  }
}
