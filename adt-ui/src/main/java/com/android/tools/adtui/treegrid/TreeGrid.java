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

import com.android.annotations.VisibleForTesting;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.Condition;
import com.intellij.ui.HideableDecorator;
import com.intellij.ui.components.JBList;
import com.intellij.ui.speedSearch.FilteringListModel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
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
  private final FocusListener myFocusListener;
  private final MouseListener myMouseListener;
  private final ListSelectionListener myListSelectionListener;
  private AbstractTreeStructure myModel;
  private boolean myFiltered;
  private int myLastMidX = -1;

  public TreeGrid(final @NotNull AbstractTreeStructure model) {
    this();
    setModelWithSectionHeaders(model);
  }

  public TreeGrid() {
    super(BoxLayout.Y_AXIS);
    setFocusTraversalPolicyProvider(true);
    setFocusTraversalPolicy(new MyFocusTraversalPolicy());
    mySectionToComponent = new IdentityHashMap<>();
    myLists = new ArrayList<>();
    myHideables = new ArrayList<>();
    myKeyListener = new MyKeyListener();
    myFocusListener = new MyFocusListener();
    myMouseListener = new MyMouseListener();
    myListSelectionListener = new MyListSelectionListener();
    addListSelectionListener(event -> {
      if (event.getValueIsAdjusting()) {
        return;
      }
      Object source = event.getSource();
      if (source instanceof JList) {
        JList sourceList = (JList)source;
        if (sourceList.getSelectedIndex() > -1) {
          for (JList<T> list : myLists) {
            if (list != sourceList) {
              list.clearSelection();
            }
          }
        }
      }
    });
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

  @Nullable
  public AbstractTreeStructure getModel() {
    return myModel;
  }

  private void setModel(@NotNull AbstractTreeStructure model, boolean showSectionHeaders) {
    myModel = model;

    // using the AbstractTreeStructure instead of the model as the actual TreeModel when used with IJ components
    // works in a very strange way, each time you expand or contract a node it will add or remove all its children.
    Object root = model.getRootElement();
    Object[] sections = model.getChildElements(root);

    mySectionToComponent.clear();
    myLists.clear();
    myHideables.clear();
    removeAll();
    setAutoscrolls(false);

    for (Object section : sections) {
      String name = section.toString();

      FilteringListModel<T> listModel = new FilteringListModel<>(new AbstractListModel() {
        @Override
        public int getSize() {
          return myModel.getChildElements(section).length;
        }

        @Override
        public Object getElementAt(int index) {
          return myModel.getChildElements(section)[index];
        }
      });
      listModel.refilter(); // Needed as otherwise the filtered list does not show any content.

      //noinspection unchecked
      JList<T> list = new JBList(listModel);
      list.setAutoscrolls(false);
      list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      list.setVisibleRowCount(-1);
      list.setName(name); // for tests to find the right list
      list.addKeyListener(myKeyListener);
      list.addFocusListener(myFocusListener);
      list.addMouseListener(myMouseListener);
      list.addListSelectionListener(myListSelectionListener);

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
    for (JList<T> list : myLists) {
      list.setTransferHandler(handler);
      if (!GraphicsEnvironment.isHeadless()) {
        list.setDragEnabled(handler != null);
      }
    }
  }

  public void addListSelectionListener(@NotNull ListSelectionListener lsl) {
    listenerList.add(ListSelectionListener.class, lsl);
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

  @VisibleForTesting
  @Nullable
  public JList<T> getSelectedList() {
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

  @Nullable
  public T getSelectedVisibleElement() {
    JList<T> list = getSelectedList();
    return list != null && list.isVisible() ? list.getSelectedValue() : null;
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

  public int getFilterMatchCount() {
    if (!isFiltered()) {
      return -1;
    }
    int count = 0;
    for (JList<T> list : myLists) {
      ListModel<T> model = list.getModel();
      count += model.getSize();
    }
    return count;
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

  @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
  @NotNull
  public List<JList<T>> getLists() {
    return myLists;
  }

  @Override
  protected void processComponentKeyEvent(@NotNull KeyEvent event) {
    if (event.getID() != KeyEvent.KEY_PRESSED) {
      return;
    }
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

  private static boolean verticalOverlap(@NotNull Rectangle bounds1, @NotNull Rectangle bounds2) {
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

  /**
   * Forward all {@link KeyEvent} from the embedded {@link JList} components to the
   * {@link KeyListener} of {@link TreeGrid}.
   * We perform our own key handling in {@link #processComponentKeyEvent}.
   */
  private class MyKeyListener implements KeyListener {

    @Override
    public void keyPressed(@NotNull KeyEvent event) {
      processKeyEvent(event);
    }

    @Override
    public void keyTyped(@NotNull KeyEvent event) {
      processKeyEvent(event);
    }

    @Override
    public void keyReleased(@NotNull KeyEvent event) {
      processKeyEvent(event);
    }
  }

  /**
   * Forward {@link FocusEvent} from the embedded {@link JList} components to the
   * {@link FocusListener} of {@link TreeGrid}.
   * Only forward the events where focus is gained/lost from an external component.
   */
  private class MyFocusListener implements FocusListener {

    @Override
    public void focusGained(@NotNull FocusEvent event) {
      Component opposite = event.getOppositeComponent();
      if (opposite == null || !SwingUtilities.isDescendingFrom(opposite, TreeGrid.this)) {
        processFocusEvent(event);
      }
    }

    @Override
    public void focusLost(@NotNull FocusEvent event) {
      Component opposite = event.getOppositeComponent();
      if (opposite == null || !SwingUtilities.isDescendingFrom(opposite, TreeGrid.this)) {
        processFocusEvent(event);
      }
    }
  }

  /**
   * Forward all {@link MouseEvent} from the embedded {@link JList} components to the
   * {@link MouseListener} of {@link TreeGrid}.
   */
  private class MyMouseListener implements MouseListener {

    @Override
    public void mouseClicked(@NotNull MouseEvent event) {
      processMouseEvent(event);
    }

    @Override
    public void mousePressed(@NotNull MouseEvent event) {
      processMouseEvent(event);
    }

    @Override
    public void mouseReleased(@NotNull MouseEvent event) {
      processMouseEvent(event);
    }

    @Override
    public void mouseEntered(@NotNull MouseEvent event) {
      processMouseEvent(event);
    }

    @Override
    public void mouseExited(@NotNull MouseEvent event) {
      processMouseEvent(event);
    }
  }

  /**
   * Forward all {@link ListSelectionEvent} from the embedded {@link JList} components to the
   * {@link ListSelectionListener} of {@link TreeGrid}.
   */
  private class MyListSelectionListener implements ListSelectionListener {

    @Override
    public void valueChanged(@NotNull ListSelectionEvent event) {
      for (ListSelectionListener listener : listenerList.getListeners(ListSelectionListener.class)) {
        listener.valueChanged(event);
      }
    }
  }

  /**
   * The purpose of this {@link FocusTraversalPolicy} is to treat the lists in the {@link TreeGrid}
   * as one tab stop. That means the next or previous component is always outside of this container.
   * And the only focusable component is the currently selected list.
   */
  private class MyFocusTraversalPolicy extends FocusTraversalPolicy {

    @Override
    public Component getComponentAfter(Container aContainer, Component aComponent) {
      return null;
    }

    @Override
    public Component getComponentBefore(Container aContainer, Component aComponent) {
      return null;
    }

    @Override
    public Component getFirstComponent(Container aContainer) {
      return getSelectedList();
    }

    @Override
    public Component getLastComponent(Container aContainer) {
      return getSelectedList();
    }

    @Override
    public Component getDefaultComponent(Container aContainer) {
      return getSelectedList();
    }
  }
}
