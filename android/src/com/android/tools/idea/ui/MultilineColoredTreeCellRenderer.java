/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.ui;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.LoadingNode;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.WideSelectionTreeUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.TreeUI;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public abstract class MultilineColoredTreeCellRenderer extends WrapAwareColoredComponent implements TreeCellRenderer {

  @NonNls protected static final String FONT_PROPERTY_NAME = "font";
  private static final           Icon   LOADING_NODE_ICON  = new EmptyIcon(8, 16);

  @NotNull private final Insets myLabelInsets = new Insets(1, 2, 1, 2);

  @Nullable private String myPrefix;

  private int myPrefixWidth;
  private int myMinHeight;

  /**
   * Defines whether the tree is selected or not
   */
  protected boolean mySelected;
  /**
   * Defines whether the tree has focus or not
   */
  private   boolean myFocused;
  private   boolean myFocusedCalculated;

  @Nullable protected JTree myTree;

  private boolean myOpaque = true;

  protected MultilineColoredTreeCellRenderer() {
    setWrapText(true);
    addPropertyChangeListener(new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        if (FONT_PROPERTY_NAME.equalsIgnoreCase(evt.getPropertyName())) {
          onFontChanged();
        }
      }
    });
  }

  protected void setMinHeight(int height) {
    myMinHeight = height;
  }

  private void onFontChanged() {
    resetTextLayoutCache();
  }

  @NotNull
  private FontMetrics getCurrFontMetrics() {
    return getFontMetrics(getFont());
  }

  public void setText(@NotNull String[] lines, @Nullable String prefix) {
    myPrefix = prefix;
    for (int i = 0; i < lines.length; i++) {
      append(lines[i]);
      if (i < lines.length - 1) {
        appendLineBreak();
      }
    }
  }

  @Override
  protected void beforePaintText(@NotNull Graphics g, int x, int textBaseLine) {
    if (!StringUtil.isEmpty(myPrefix)) {
      g.drawString(myPrefix, x - myPrefixWidth + 1, textBaseLine);
    }
  }

  @NotNull
  @Override
  public Dimension getMinimumSize() {
    Dimension preferredSize = getPreferredSize();
    Dimension result = new Dimension(preferredSize);
    Insets padding = getIpad();
    result.width = Math.max(result.width, padding.left + padding.right);
    result.height = Math.max(myMinHeight, Math.max(result.height, padding.top + padding.bottom));
    return result;
  }

  private static int getChildIndent(@NotNull JTree tree) {
    TreeUI newUI = tree.getUI();
    if (newUI instanceof BasicTreeUI) {
      BasicTreeUI ui = (BasicTreeUI)newUI;
      return ui.getLeftChildIndent() + ui.getRightChildIndent();
    }
    else {
      return ((Integer)UIUtil.getTreeLeftChildIndent()).intValue() + ((Integer)UIUtil.getTreeRightChildIndent()).intValue();
    }
  }

  private static int getAvailableWidth(@NotNull Object forValue, @NotNull JTree tree) {
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)forValue;
    int busyRoom = tree.getInsets().left + tree.getInsets().right + getChildIndent(tree) * node.getLevel();
    return tree.getVisibleRect().width - busyRoom - 2;
  }



  protected abstract void initComponent(@NotNull JTree tree,
                                        @Nullable Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus);

  public void customizeCellRenderer(@NotNull JTree tree,
                                    @NotNull Object value,
                                    boolean selected,
                                    boolean expanded,
                                    boolean leaf,
                                    int row,
                                    boolean hasFocus)
  {
    setFont(UIUtil.getTreeFont());

    initComponent(tree, value, selected, expanded, leaf, row, hasFocus);

    int availWidth = getAvailableWidth(value, tree);
    if (availWidth > 0) {
      setSize(availWidth, 100);     // height will be calculated automatically
    }

    int leftInset = myLabelInsets.left;

    Icon icon = getIcon();
    if (icon != null) {
      leftInset += icon.getIconWidth() + 2;
    }

    if (!StringUtil.isEmpty(myPrefix)) {
      myPrefixWidth = getCurrFontMetrics().stringWidth(myPrefix) + 5;
      leftInset += myPrefixWidth;
    }

    setIpad(new Insets(myLabelInsets.top, leftInset, myLabelInsets.bottom, myLabelInsets.right));
    if (icon != null) {
      setMinHeight(icon.getIconHeight());
    }
    else {
      setMinHeight(1);
    }

    setSize(getPreferredSize());
    resetTextLayoutCache();
  }

  @SuppressWarnings("IfStatementWithIdenticalBranches")
  @Override
  public final Component getTreeCellRendererComponent(@NotNull JTree tree,
                                                      @NotNull Object value,
                                                      boolean selected,
                                                      boolean expanded,
                                                      boolean leaf,
                                                      int row,
                                                      boolean hasFocus)
  {
    myTree = tree;

    clear();

    mySelected = selected;
    myFocusedCalculated = false;

    // We paint background if and only if tree path is selected and tree has focus.
    // If path is selected and tree is not focused then we just paint focused border.
    if (UIUtil.isFullRowSelectionLAF()) {
      setBackground(selected ? UIUtil.getTreeSelectionBackground() : null);
    }
    else if (tree.getUI() instanceof WideSelectionTreeUI && ((WideSelectionTreeUI)tree.getUI()).isWideSelection()) {
      setPaintFocusBorder(false);
      if (selected) {
        setBackground(hasFocus ? UIUtil.getTreeSelectionBackground() : UIUtil.getTreeUnfocusedSelectionBackground());
      }
    }
    else if (selected) {
      setPaintFocusBorder(true);
      if (isFocused()) {
        setBackground(UIUtil.getTreeSelectionBackground());
      }
      else {
        setBackground(null);
      }
    }
    else {
      setBackground(null);
    }

    if (value instanceof LoadingNode) {
      setForeground(JBColor.GRAY);
      setIcon(LOADING_NODE_ICON);
    }
    else {
      setForeground(tree.getForeground());
      setIcon(null);
    }

    if (UIUtil.isUnderGTKLookAndFeel()) {
      super.setOpaque(false);  // avoid nasty background
      super.setIconOpaque(false);
    }
    else if (UIUtil.isUnderNimbusLookAndFeel() && selected && hasFocus) {
      super.setOpaque(false);  // avoid erasing Nimbus focus frame
      super.setIconOpaque(false);
    }
    else if (tree.getUI() instanceof WideSelectionTreeUI && ((WideSelectionTreeUI)tree.getUI()).isWideSelection()) {
      super.setOpaque(false);  // avoid erasing Nimbus focus frame
      super.setIconOpaque(false);
    }
    else {
      super.setOpaque(myOpaque || selected && hasFocus || selected && isFocused()); // draw selection background even for non-opaque tree
    }

    if (tree.getUI() instanceof WideSelectionTreeUI && UIUtil.isUnderAquaBasedLookAndFeel()) {
      setMyBorder(null);
      setIpad(new Insets(0, 2, 0, 2));
    }

    customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus);

    return this;
  }

  @NotNull
  public static JScrollPane installRenderer(@NotNull final JTree tree, @NotNull final MultilineColoredTreeCellRenderer renderer) {
    final TreeCellRenderer defaultRenderer = tree.getCellRenderer();

    JScrollPane scrollPane = new JBScrollPane(tree){
      private int myAddRemoveCounter = 0;
      private boolean myShouldResetCaches = false;
      @Override
      public void setSize(Dimension d) {
        boolean isChanged = getWidth() != d.width || myShouldResetCaches;
        super.setSize(d);
        if (isChanged) resetCaches();
      }

      @Override
      public void setBounds(int x, int y, int width, int height) {
        boolean isChanged = width != getWidth() || myShouldResetCaches;
        super.setBounds(x, y, width, height);
        if (isChanged) resetCaches();
      }

      private void resetCaches() {
        resetHeightCache(tree, defaultRenderer, renderer);
        myShouldResetCaches = false;
      }

      @Override
      public void addNotify() {
        super.addNotify();
        if (myAddRemoveCounter == 0) myShouldResetCaches = true;
        myAddRemoveCounter++;
      }

      @Override
      public void removeNotify() {
        super.removeNotify();
        myAddRemoveCounter--;
      }
    };
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
    scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

    tree.setCellRenderer(renderer);

    scrollPane.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        resetHeightCache(tree, defaultRenderer, renderer);
      }

      @Override
      public void componentShown(ComponentEvent e) {
        // componentResized not called when adding to opened tool window.
        // Seems to be BUG#4765299, however I failed to create same code to reproduce it.
        // To reproduce it with IDEA: 1. remove this method, 2. Start any Ant task, 3. Keep message window open 4. start Ant task again.
        resetHeightCache(tree, defaultRenderer, renderer);
      }
    });

    return scrollPane;
  }

  private static void resetHeightCache(@NotNull final JTree tree,
                                       @NotNull final TreeCellRenderer defaultRenderer,
                                       @NotNull final MultilineColoredTreeCellRenderer renderer) {
    tree.setCellRenderer(defaultRenderer);
    tree.setCellRenderer(renderer);
  }

  @Nullable
  public JTree getTree() {
    return myTree;
  }

  protected final boolean isFocused() {
    if (!myFocusedCalculated) {
      myFocused = calcFocusedState();
      myFocusedCalculated = true;
    }
    return myFocused;
  }

  protected boolean calcFocusedState() {
    return myTree != null && myTree.hasFocus();
  }

  @Override
  public void setOpaque(boolean isOpaque) {
    myOpaque = isOpaque;
    super.setOpaque(isOpaque);
  }

  @Nullable
  @Override
  public Font getFont() {
    Font font = super.getFont();

    // Cell renderers could have no parent and no explicit set font.
    // Take tree font in this case.
    if (font != null) return font;
    JTree tree = getTree();
    return tree != null ? tree.getFont() : null;
  }

  /**
   * When the item is selected then we use default tree's selection foreground.
   * It guaranties readability of selected text in any LAF.
   */
  @Override
  public void append(@NotNull @Nls String fragment, @NotNull SimpleTextAttributes attributes, boolean isMainText) {
    if (mySelected && isFocused()) {
      super.append(fragment, new SimpleTextAttributes(attributes.getStyle(), UIUtil.getTreeSelectionForeground()), isMainText);
    }
    else if (mySelected && UIUtil.isUnderAquaBasedLookAndFeel()) {
      super.append(fragment, new SimpleTextAttributes(attributes.getStyle(), UIUtil.getTreeForeground()), isMainText);
    }
    else {
      super.append(fragment, attributes, isMainText);
    }
  }
}
