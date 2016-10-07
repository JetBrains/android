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
package com.android.tools.idea.gradle.structure.configurables.ui.treeview;

import com.google.common.collect.Lists;
import com.intellij.ide.util.treeView.AbstractTreeUi;
import com.intellij.ide.util.treeView.TreeVisitor;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.SpeedSearchComparator;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.List;

import static com.android.tools.idea.gradle.structure.configurables.ui.UiUtil.revalidateAndRepaint;
import static com.google.common.base.Strings.nullToEmpty;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.util.ui.UIUtil.*;
import static java.awt.Font.BOLD;
import static java.awt.event.KeyEvent.*;
import static java.lang.Character.isLetterOrDigit;
import static javax.swing.SwingUtilities.getAncestorOfClass;

public class TreeBuilderSpeedSearch extends SpeedSearchSupply {
  private static final TreePath[] EMPTY_TREE_PATH = new TreePath[0];
  @NonNls private static final String ENTERED_PREFIX_PROPERTY_NAME = "enteredPrefix";

  @NotNull private final AbstractBaseTreeBuilder myTreeBuilder;
  @NotNull private final JTree myTree;
  @NotNull private final PropertyChangeSupport myChangeSupport = new PropertyChangeSupport(this);
  @NotNull private final SpeedSearchComparator myComparator = new SpeedSearchComparator(false);

  private String myRecentEnteredPrefix;
  private SearchPopup mySearchPopup;
  private JLayeredPane myPopupLayeredPane;

  public static void installTo(@NotNull AbstractBaseTreeBuilder treeBuilder) {
    new TreeBuilderSpeedSearch(treeBuilder);
  }

  private TreeBuilderSpeedSearch(@NotNull AbstractBaseTreeBuilder treeBuilder) {
    myTreeBuilder = treeBuilder;
    myTree = myTreeBuilder.getUi().getTree();

    myTree.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentHidden(ComponentEvent event) {
        manageSearchPopup(null);
      }

      @Override
      public void componentMoved(ComponentEvent event) {
        moveSearchPopup();
      }

      @Override
      public void componentResized(ComponentEvent event) {
        moveSearchPopup();
      }
    });
    myTree.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        manageSearchPopup(null);
      }
    });
    myTree.addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped(KeyEvent e) {
        processKeyEvent(e);
      }

      @Override
      public void keyPressed(KeyEvent e) {
        processKeyEvent(e);
      }
    });

    installSupplyTo(myTree);
  }

  private void manageSearchPopup(@Nullable SearchPopup searchPopup) {
    if (mySearchPopup != null) {
      myPopupLayeredPane.remove(mySearchPopup);
      revalidateAndRepaint(myPopupLayeredPane);
      myPopupLayeredPane = null;
    }

    if (!myTree.isShowing()) {
      mySearchPopup = null;
    }
    else {
      mySearchPopup = searchPopup;
    }

    if (mySearchPopup == null || !myTree.isDisplayable()) {
      return;
    }

    JRootPane rootPane = myTree.getRootPane();
    if (rootPane != null) {
      myPopupLayeredPane = rootPane.getLayeredPane();
    }
    else {
      myPopupLayeredPane = null;
    }

    if (myPopupLayeredPane == null) {
      return;
    }

    myPopupLayeredPane.add(mySearchPopup, JLayeredPane.POPUP_LAYER);
    moveSearchPopup();
  }

  private void moveSearchPopup() {
    if (mySearchPopup == null || myPopupLayeredPane == null) {
      return;
    }
    Window window = (Window)getAncestorOfClass(Window.class, myTree);
    Point windowLocation;
    if (window instanceof JDialog) {
      windowLocation = ((JDialog)window).getContentPane().getLocationOnScreen();
    }
    else if (window instanceof JFrame) {
      windowLocation = ((JFrame)window).getContentPane().getLocationOnScreen();
    }
    else {
      windowLocation = window.getLocationOnScreen();
    }

    Rectangle visibleRect = getComponentVisibleRect();
    Dimension searchPopupPreferredSize = mySearchPopup.getPreferredSize();
    Point popUpLayeredPaneLocation = myPopupLayeredPane.getLocationOnScreen();
    Point locationOnScreen = getComponentLocationOnScreen();

    int y = visibleRect.y + locationOnScreen.y - popUpLayeredPaneLocation.y - searchPopupPreferredSize.height;
    y = Math.max(y, windowLocation.y - popUpLayeredPaneLocation.y);

    mySearchPopup.setLocation(locationOnScreen.x - popUpLayeredPaneLocation.x + visibleRect.x, y);
    mySearchPopup.setSize(searchPopupPreferredSize);
    mySearchPopup.setVisible(true);
    mySearchPopup.validate();
  }

  protected Rectangle getComponentVisibleRect() {
    return myTree.getVisibleRect();
  }

  protected Point getComponentLocationOnScreen() {
    return myTree.getLocationOnScreen();
  }

  protected void processKeyEvent(@NotNull KeyEvent e) {
    if (e.isAltDown() || e.isShiftDown()) {
      return;
    }
    if (mySearchPopup != null) {
      mySearchPopup.processKeyEvent(e);
      return;
    }
    if (e.getID() == KEY_TYPED) {
      if (!isReallyTypedEvent(e)) {
        return;
      }

      char c = e.getKeyChar();
      if (isLetterOrDigit(c) || c == '_' || c == '*' || c == '/' || c == ':' || c == '.' || c == '#' || c == '$') {
        manageSearchPopup(new SearchPopup(String.valueOf(c)));
        e.consume();
      }
    }
  }

  @Override
  @Nullable
  public String getEnteredPrefix() {
    return mySearchPopup != null ? mySearchPopup.mySearchField.getText() : null;
  }

  @Override
  @Nullable
  public Iterable<TextRange> matchingFragments(@NotNull String text) {
    String recentSearchText = myComparator.getRecentSearchText();
    return isNotEmpty(recentSearchText) ? myComparator.matchingFragments(recentSearchText, text) : null;
  }

  @Override
  public void refreshSelection() {
    if (mySearchPopup != null) {
      mySearchPopup.refreshSelection();
    }
  }

  @Override
  public boolean isPopupActive() {
    return mySearchPopup != null && mySearchPopup.isVisible();
  }

  @Override
  public void addChangeListener(@NotNull PropertyChangeListener listener) {
    myChangeSupport.addPropertyChangeListener(listener);
  }

  @Override
  public void removeChangeListener(@NotNull PropertyChangeListener listener) {
    myChangeSupport.removePropertyChangeListener(listener);
  }

  private void fireStateChanged() {
    String enteredPrefix = getEnteredPrefix();
    myChangeSupport.firePropertyChange(ENTERED_PREFIX_PROPERTY_NAME, myRecentEnteredPrefix, enteredPrefix);
    myRecentEnteredPrefix = enteredPrefix;
  }

  @Override
  public void findAndSelectElement(@NotNull String searchQuery) {
    String pattern = searchQuery.trim();
    clearSelection(myTree);

    if (searchQuery.isEmpty()) {
      return;
    }

    ActionCallback initialized = myTreeBuilder.getInitialized();
    initialized.doWhenDone(() -> {
      List<AbstractPsModelNode> nodes = Lists.newArrayList();
      myTreeBuilder.accept(AbstractPsModelNode.class, new TreeVisitor<AbstractPsModelNode>() {
        @Override
        public boolean visit(@NotNull AbstractPsModelNode node) {
          if (isMatchingElement(node, pattern)) {
            nodes.add(node);
          }
          return false;
        }
      });

      Color foreground = nodes.isEmpty() ? JBColor.red : getToolTipForeground();
      if (mySearchPopup != null) {
        mySearchPopup.mySearchField.setForeground(foreground);
      }

      if (nodes.isEmpty()) {
        return;
      }

      Runnable onDone = () -> {
        myTreeBuilder.expandParents(nodes);
        myTreeBuilder.scrollToFirstSelectedRow();
      };
      myTreeBuilder.getUi().userSelect(nodes.toArray(), () -> {
        AbstractTreeUi ui = myTreeBuilder.getUi();
        if (ui != null) {
          ui.executeUserRunnable(onDone);
        }
        else {
          onDone.run();
        }
      }, false, false);
    });
  }

  private static void clearSelection(@NotNull JTree tree) {
    tree.setSelectionPaths(EMPTY_TREE_PATH);
  }

  @NotNull
  private List<AbstractPsModelNode> findNodes(@NotNull String searchQuery) {
    String pattern = searchQuery.trim();

    List<AbstractPsModelNode> nodes = Lists.newArrayList();
    ActionCallback initialized = myTreeBuilder.getInitialized();
    initialized.doWhenDone(() -> myTreeBuilder.accept(AbstractPsModelNode.class, new TreeVisitor<AbstractPsModelNode>() {
      @Override
      public boolean visit(@NotNull AbstractPsModelNode node) {
        if (isMatchingElement(node, pattern)) {
          nodes.add(node);
        }
        return false;
      }
    }));
    return nodes;
  }

  private boolean isMatchingElement(@NotNull AbstractPsModelNode node, @Nullable String pattern) {
    String text = node.getName();
    return text != null && compare(text, pattern);
  }

  private boolean compare(@NotNull String text, @Nullable String pattern) {
    return pattern != null && myComparator.matchingFragments(pattern, text) != null;
  }

  private class SearchPopup extends JPanel {
    @NotNull private final SearchField mySearchField;

    SearchPopup(@Nullable String initialString) {
      super(new BorderLayout());
      JBColor background = new JBColor(getToolTipBackground().brighter(), Gray._111);

      mySearchField = new SearchField();
      mySearchField.setBorder(null);
      mySearchField.setBackground(background);
      mySearchField.setForeground(getToolTipForeground());
      mySearchField.setDocument(new PlainDocument() {
        @Override
        public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
          String oldText;
          try {
            oldText = getText(0, getLength());
          }
          catch (BadLocationException e1) {
            oldText = "";
          }

          String newText = oldText.substring(0, offs) + str + oldText.substring(offs);
          super.insertString(offs, str, a);
          if (findNodes(newText).isEmpty()) {
            mySearchField.setForeground(JBColor.RED);
          }
          else {
            mySearchField.setForeground(getToolTipForeground());
          }
        }
      });
      mySearchField.setText(nullToEmpty(initialString));

      JLabel searchLabel = new JLabel(" Search for: ");
      searchLabel.setFont(searchLabel.getFont().deriveFont(BOLD));
      searchLabel.setForeground(getToolTipForeground());

      setBorder(BorderFactory.createLineBorder(JBColor.GRAY, 1));
      setBackground(background);

      add(searchLabel, BorderLayout.WEST);
      add(mySearchField, BorderLayout.EAST);

      refreshSelection();
    }

    @Override
    public void processKeyEvent(KeyEvent e) {
      mySearchField.processKeyEvent(e);
      if (e.isConsumed()) {
        refreshSelection();
      }
    }

    void refreshSelection() {
      if (mySearchPopup != null) {
        mySearchPopup.setSize(mySearchPopup.getPreferredSize());
        mySearchPopup.validate();
      }
      findAndSelectElement(mySearchField.getText());
      fireStateChanged();
    }
  }

  private class SearchField extends JTextField {
    SearchField() {
      setFocusable(false);
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension preferredSize = super.getPreferredSize();
      preferredSize.width = getFontMetrics(getFont()).stringWidth(getText()) + 10;
      return preferredSize;
    }

    @Override
    public void processKeyEvent(KeyEvent e) {
      int keyCode = e.getKeyCode();
      if (keyCode == VK_BACK_SPACE && getDocument().getLength() == 0) {
        e.consume();
        return;
      }
      if (keyCode == VK_ENTER ||
          keyCode == VK_ESCAPE ||
          keyCode == VK_PAGE_UP ||
          keyCode == VK_PAGE_DOWN ||
          keyCode == VK_LEFT ||
          keyCode == VK_RIGHT) {
        manageSearchPopup(null);
        if (keyCode == VK_ESCAPE) {
          e.consume();
        }
        return;
      }
      super.processKeyEvent(e);
      if (keyCode == VK_BACK_SPACE) {
        e.consume();
      }
    }
  }
}
