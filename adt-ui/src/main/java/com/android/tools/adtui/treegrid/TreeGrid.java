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
package com.android.tools.adtui.treegrid;

import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.Condition;
import com.intellij.ui.HideableDecorator;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.speedSearch.FilteringListModel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * A list/grid of items that are split into sections.
 * A AbstractTreeStructure is used to create the gridlist.
 * the children of the root are the sections, and their leaves are the items.
 */
public class TreeGrid<T> extends Box {

  private final Map<Object, JComponent> mySectionToComponent;
  private final List<JList<T>> myLists;
  private final List<HideableDecorator> myHideables;
  private final KeyListener myKeyListener;
  private boolean myFiltered;

  public TreeGrid(final @NotNull AbstractTreeStructure model) {
    this();
    setModelWithSectionHeaders(model);
  }

  public TreeGrid() {
    super(BoxLayout.Y_AXIS);
    mySectionToComponent = new IdentityHashMap<>();
    myLists = new ArrayList<>();
    myHideables = new ArrayList<>();
    myKeyListener = new MyKeyListener();
  }

  @Override
  public void invalidate() {
    super.invalidate();

    // The following lines makes the background of the JSeparator the same as
    // all the JLists thus making this appear as one list control with separators.
    // Make the change here such that Look & Feel changes get the correct background color.
    // (Used in the Palette)
    setBackground(UIUtil.getListBackground());
    setOpaque(true);
  }

  public void setModel(@NotNull AbstractTreeStructure model) {
    setModel(model, false);
  }

  public void setModelWithSectionHeaders(@NotNull AbstractTreeStructure model) {
    setModel(model, true);
  }

  private void setModel(@NotNull AbstractTreeStructure model, boolean showSectionHeaders) {
    // using the AbstractTreeStructure instead of the model as the actual TreeModel when used with IJ components
    // works in a very strange way, each time you expand or contract a node it will add or remove all its children.
    Object root = model.getRootElement();
    Object[] sections = model.getChildElements(root);

    mySectionToComponent.clear();
    myLists.clear();
    myHideables.clear();
    removeAll();
    setAutoscrolls(false);

    ListSelectionListener listSelectionListener = e -> {
      if (e.getValueIsAdjusting()) {
        return;
      }
      ListSelectionModel sourceSelectionModel = (ListSelectionModel)e.getSource();
      if (!sourceSelectionModel.isSelectionEmpty()) {
        for (JList<T> aList : myLists) {
          if (sourceSelectionModel != aList.getSelectionModel()) {
            aList.clearSelection();
          }
        }
      }
    };

    for (Object section : sections) {
      String name = section.toString();

      FilteringListModel<T> listModel = new FilteringListModel<>(new AbstractListModel() {
        @Override
        public int getSize() {
          return model.getChildElements(section).length;
        }

        @Override
        public Object getElementAt(int index) {
          return model.getChildElements(section)[index];
        }
      });
      listModel.refilter(); // Needed as otherwise the filtered list does not show any content.

      // JBList does not work with HORIZONTAL_WRAP
      //noinspection UndesirableClassUsage,unchecked
      JList<T> list = new JList<>(listModel);
      list.setAutoscrolls(false);
      list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      list.setVisibleRowCount(-1);
      list.getSelectionModel().addListSelectionListener(listSelectionListener);
      list.setName(name); // for tests to find the right list
      list.addKeyListener(myKeyListener);
      new ListSpeedSearch(list);

      myLists.add(list);
      if (showSectionHeaders) {
        JPanel panel = new JPanel(new BorderLayout()) {// must be BorderLayout for HideableDecorator to work
          @Override
          public Dimension getMaximumSize() {
            return new Dimension(super.getMaximumSize().width, super.getPreferredSize().height);
          }
        };
        HideableDecorator hidyPanel = new HideableDecorator(panel, name, false);
        myHideables.add(hidyPanel);
        hidyPanel.setContentComponent(list);
        add(panel);
        mySectionToComponent.put(section, panel);
      }
      else {
        if (getComponentCount() > 0) {
          add(new JSeparator());
        }
        list.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        add(list);
        mySectionToComponent.put(section, list);
      }
    }
  }

  public void setVisibleSection(@Nullable Object section) {
    JComponent visible = section != null ? mySectionToComponent.get(section) : null;
    for (Component component : getComponents()) {
      component.setVisible(visible == null);
    }
    if (visible != null) {
      visible.setVisible(true);
    }
  }

  @Override
  public void setTransferHandler(@Nullable TransferHandler handler) {
    if (GraphicsEnvironment.isHeadless()) {
      return;
    }
    for (JList<T> list : myLists) {
      list.setTransferHandler(handler);
      list.setDragEnabled(handler != null);
    }
  }

  public void addListSelectionListener(@NotNull ListSelectionListener lsl) {
    for (JList<T> list : myLists) {
      list.getSelectionModel().addListSelectionListener(lsl);
    }
  }

  public void setCellRenderer(@NotNull ListCellRenderer<T> cellRenderer) {
    for (JList<T> list : myLists) {
      list.setCellRenderer(cellRenderer);
    }
  }

  public void setFixedCellWidth(int width) {
    for (JList<T> list : myLists) {
      list.setFixedCellWidth(width);
    }
  }

  public void setFixedCellHeight(int height) {
    for (JList<T> list : myLists) {
      list.setFixedCellHeight(height);
    }
  }

  public void expandAll() {
    for (HideableDecorator hidyPanel : myHideables) {
      hidyPanel.setOn(true);
    }
  }

  @Nullable
  private JList<T> getSelectedList() {
    for (JList<T> list : myLists) {
      if (list.getSelectedIndex() > -1) {
        return list;
      }
    }
    return null;
  }

  @Override
  public void requestFocus() {
    JComponent component = getFocusRecipient();
    if (component != null) {
      component.requestFocus();
    }
  }

  @Nullable
  public JComponent getFocusRecipient() {
    JList<T> firstVisible = null;
    for (JList<T> list : myLists) {
      if (list.isVisible()) {
        //noinspection IfStatementWithIdenticalBranches
        if (list.getSelectedIndex() != -1) {
          return list;
        }
        else if (firstVisible == null && list.getModel().getSize() > 0) {
          firstVisible = list;
        }
      }
    }
    if (firstVisible != null) {
      setSelectedElement(firstVisible.getModel().getElementAt(0));
    }
    return firstVisible;
  }

  @Nullable
  public T getSelectedElement() {
    JList<T> list = getSelectedList();
    return list != null ? list.getSelectedValue() : null;
  }

  public void setSelectedElement(@Nullable T selectedElement) {
    for (JList<T> list : myLists) {
      if (selectedElement == null) {
        list.clearSelection();
      }
      else {
        for (int i = 0; i < list.getModel().getSize(); i++) {
          if (list.getModel().getElementAt(i) == selectedElement) {
            list.setSelectedIndex(i);
            ensureIndexVisible(list, i);
            return;
          }
        }
      }
    }
  }

  // we do this in invokeLater to make sure things like expandAll() have had their effect.
  private void ensureIndexVisible(@NotNull JList<T> list, int index) {
    // Use an invokeLater to ensure that layout has been performed (such that
    // the coordinate math of looking up the list position correctly gets
    // the offset of the list containing the match)
    ApplicationManager.getApplication().invokeLater(() -> {
      //list.ensureIndexIsVisible(index);
      Rectangle cellBounds = list.getCellBounds(index, index);
      if (cellBounds != null) {
        list.getBounds();
        Rectangle rectangle = SwingUtilities.convertRectangle(list, cellBounds, this);
        scrollRectToVisible(rectangle);
      }
    }, ModalityState.any());
  }

  @Override
  @SuppressWarnings("NonSynchronizedMethodOverridesSynchronizedMethod")
  public void addMouseListener(@NotNull MouseListener l) {
    for (JList<T> list : myLists) {
      list.addMouseListener(l);
    }
  }

  @Override
  @SuppressWarnings("NonSynchronizedMethodOverridesSynchronizedMethod")
  public void removeMouseListener(@NotNull MouseListener l) {
    for (JList<T> list : myLists) {
      list.removeMouseListener(l);
    }
  }

  public void setLayoutOrientation(int mode) {
    for (JList<T> list : myLists) {
      list.setLayoutOrientation(mode);
    }
  }

  public void setFilter(@Nullable Condition<T> condition) {
    myFiltered = condition != null;
    for (JList<T> list : myLists) {
      //noinspection unchecked
      ((FilteringListModel<T>)list.getModel()).setFilter(condition);
    }
  }

  public boolean isFiltered() {
    return myFiltered;
  }

  public void selectIfUnique() {
    T single = findSingleItem();
    if (single != null) {
      setSelectedElement(single);
    }
  }

  @Nullable
  private T findSingleItem() {
    T singleMatch = null;
    boolean found = false;

    for (JList<T> list : myLists) {
      ListModel<T> model = list.getModel();
      int size = model.getSize();
      if (size == 1) {
        if (found) {
          return null;
        } else {
          found = true;
          singleMatch = model.getElementAt(0);
        }
      } else if (size > 1) {
        return null;
      }
    }

    return singleMatch;
  }

  public void selectFirst() {
    for (JList<T> list : myLists) {
      ListModel<T> model = list.getModel();
      int size = model.getSize();
      if (size > 0) {
        T item = model.getElementAt(0);
        setSelectedElement(item);
        list.requestFocus();
        ensureIndexVisible(list, 0);
        return;
      }
    }
  }

  @TestOnly
  @Nullable
  JList<T> getSelectedComponent() {
    return getSelectedList();
  }

  @TestOnly
  @NotNull
  public List<JList<T>> getLists() {
    return myLists;
  }

  private class MyKeyListener extends KeyAdapter {
    private int myLastMidX = -1;

    @Override
    public void keyPressed(@NotNull KeyEvent event) {
      Object source = event.getSource();
      if (!(source instanceof JList)) {
        return;
      }
      //noinspection unchecked
      JList<T> list = (JList<T>)source;
      boolean consumed = false;
      switch (event.getKeyCode()) {
        case KeyEvent.VK_DOWN:
          consumed = handleKeyDown(list);
          break;
        case KeyEvent.VK_UP:
          consumed = handleKeyUp(list);
          break;
        case KeyEvent.VK_LEFT:
          myLastMidX = -1;
          consumed = handleKeyLeft(list);
          break;
        case KeyEvent.VK_RIGHT:
          myLastMidX = -1;
          consumed = handleKeyRight(list);
          break;
      }
      if (consumed) {
        event.consume();
      }
    }

    private boolean handleKeyDown(@NotNull JList<T> list) {
      int selectedIndex = list.getSelectedIndex();
      if (selectedIndex < 0) {
        return false;
      }
      if (myLastMidX < 0) {
        myLastMidX = midX(list, selectedIndex);
      }
      if (!isLastRow(list, selectedIndex)) {
        int nextLineStart = findNextListStart(list, selectedIndex);
        int newSelectedItem = findBestMatchFromLeft(list, myLastMidX, nextLineStart);
        selectNewItem(list, newSelectedItem, list);
        return true;
      }
      else {
        for (int listIndex = ContainerUtil.indexOf(myLists, list) + 1; listIndex != 0 && listIndex < myLists.size(); listIndex++) {
          JList<T> nextList = myLists.get(listIndex);
          int itemCount = nextList.getModel().getSize();
          if (itemCount > 0 && nextList.isVisible()) {
            int newSelectedItem = findBestMatchFromLeft(nextList, myLastMidX, 0);
            selectNewItem(nextList, newSelectedItem, list);
            return true;
          }
        }
      }
      return false;
    }

    private boolean handleKeyUp(@NotNull JList<T> list) {
      int selectedIndex = list.getSelectedIndex();
      if (selectedIndex < 0) {
        return false;
      }
      if (myLastMidX < 0) {
        myLastMidX = midX(list, selectedIndex);
      }
      if (!isFirstRow(list, selectedIndex)) {
        int prevLineEnd = findPrevListEnd(list, selectedIndex);
        int newSelectedItem = findBestMatchFromRight(list, myLastMidX, prevLineEnd);
        selectNewItem(list, newSelectedItem, list);
        return true;
      }
      else {
        for (int listIndex = ContainerUtil.indexOf(myLists, list) - 1; listIndex >= 0; listIndex--) {
          JList<T> prevList = myLists.get(listIndex);
          int itemCount = prevList.getModel().getSize();
          if (itemCount > 0 && prevList.isVisible()) {
            int newSelectedItem = findBestMatchFromRight(prevList, myLastMidX, itemCount - 1);
            selectNewItem(prevList, newSelectedItem, list);
            return true;
          }
        }
      }
      return false;
    }

    private boolean handleKeyRight(@NotNull JList<T> list) {
      int selectedIndex = list.getSelectedIndex();
      if (selectedIndex < 0) {
        return false;
      }
      if (selectedIndex < list.getModel().getSize() - 1) {
        selectNewItem(list, selectedIndex + 1, list);
        return true;
      }
      for (int listIndex = ContainerUtil.indexOf(myLists, list) + 1; listIndex != 0 && listIndex < myLists.size(); listIndex++) {
        JList<T> nextList = myLists.get(listIndex);
        int itemCount = nextList.getModel().getSize();
        if (itemCount > 0 && nextList.isVisible()) {
          selectNewItem(nextList, 0, list);
          return true;
        }
      }
      return false;
    }

    private boolean handleKeyLeft(@NotNull JList<T> list) {
      int selectedIndex = list.getSelectedIndex();
      if (selectedIndex < 0) {
        return false;
      }
      if (selectedIndex > 0) {
        selectNewItem(list, selectedIndex - 1, list);
        return true;
      }
      for (int listIndex = ContainerUtil.indexOf(myLists, list) - 1; listIndex >= 0; listIndex--) {
        JList<T> prevList = myLists.get(listIndex);
        int itemCount = prevList.getModel().getSize();
        if (itemCount > 0 && prevList.isVisible()) {
          selectNewItem(prevList, itemCount - 1, list);
          return true;
        }
      }
      return false;
    }

    private int midX(@NotNull JList<T> list, int index) {
      Rectangle bounds = list.getCellBounds(index, index);
      return bounds.x + bounds.width / 2;
    }

    private boolean isFirstRow(@NotNull JList<T> list, int index) {
      Rectangle bounds = list.getCellBounds(index, index);
      Rectangle firstBounds = list.getCellBounds(0, 0);
      return verticalOverlap(bounds, firstBounds);
    }

    private boolean isLastRow(@NotNull JList<T> list, int index) {
      int last = list.getModel().getSize() - 1;
      Rectangle bounds = list.getCellBounds(index, index);
      Rectangle lastBounds = list.getCellBounds(last, last);
      return verticalOverlap(bounds, lastBounds);
    }

    private boolean verticalOverlap(@NotNull Rectangle bounds1, @NotNull Rectangle bounds2) {
      return bounds1.y < bounds2.y + bounds2.height && bounds1.y + bounds1.height > bounds2.y;
    }

    private int findPrevListEnd(@NotNull JList<T> list, int index) {
      Rectangle bounds = list.getCellBounds(index, index);
      int prevIndex = index;
      Rectangle prevBounds = bounds;
      while (index > 0 && verticalOverlap(bounds, prevBounds)) {
        prevIndex--;
        prevBounds = list.getCellBounds(prevIndex, prevIndex);
      }
      return prevIndex;
    }

    private int findNextListStart(@NotNull JList<T> list, int index) {
      int count = list.getModel().getSize();
      Rectangle bounds = list.getCellBounds(index, index);
      int nextIndex = index;
      Rectangle nextBounds = bounds;
      while (index < count && verticalOverlap(bounds, nextBounds)) {
        nextIndex++;
        nextBounds = list.getCellBounds(nextIndex, nextIndex);
      }
      return nextIndex;
    }

    private int findBestMatchFromLeft(@NotNull JList<T> list, int x, int startIndex) {
      int bestIndex = startIndex;
      int count = list.getModel().getSize();
      Rectangle bounds = list.getCellBounds(startIndex, startIndex);
      int y = bounds.y + bounds.height / 2;
      while (bestIndex < count && bounds.y < y && bounds.x + bounds.width < x) {
        bestIndex++;
        bounds = list.getCellBounds(bestIndex, bestIndex);
      }
      return bestIndex < count ? bestIndex : count - 1;
    }

    private int findBestMatchFromRight(@NotNull JList<T> list, int x, int startIndex) {
      int bestIndex = startIndex;
      Rectangle bounds = list.getCellBounds(bestIndex, bestIndex);
      int y = bounds.y + bounds.height / 2;
      while (bestIndex > 0 && bounds.y + bounds.height > y && bounds.x > x) {
        bestIndex--;
        bounds = list.getCellBounds(bestIndex, bestIndex);
      }
      return bestIndex;
    }

    private void selectNewItem(@NotNull JList<T> list, int index, @NotNull JList<T> prevList) {
      prevList.clearSelection();
      list.setSelectedIndex(index);
      ensureIndexVisible(list, index);
      list.requestFocus();
    }
  }
}
