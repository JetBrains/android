/*
 * Copyright (C) 2015 The Android Open Source Project
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
package org.jetbrains.android.uipreview;

import com.google.common.collect.Lists;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.openapi.util.Condition;
import com.intellij.ui.HideableDecorator;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.speedSearch.FilteringListModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.MouseListener;
import java.util.*;

/**
 * A list/grid of items that are split into sections.
 * A AbstractTreeStructure is used to create the gridlist.
 * the children of the root are the sections, and their leaves are the items.
 */
public class TreeGrid extends Box {

  private final @NotNull ArrayList<JList> myLists;
  private final @NotNull ArrayList<HideableDecorator> myHideables;

  public TreeGrid(final @NotNull AbstractTreeStructure model) {
    super(BoxLayout.Y_AXIS);

    // using the AbstractTreeStructure instead of the model as the actual TreeModel when used with IJ components
    // works in a very strange way, each time you expand or contract a node it will add or remove all its children.
    Object root = model.getRootElement();
    Object[] sections = model.getChildElements(root);

    myLists = Lists.newArrayListWithCapacity(sections.length);
    myHideables = Lists.newArrayListWithCapacity(sections.length);

    ListSelectionListener listSelectionListener = new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
          return;
        }
        ListSelectionModel sourceSelectionModel = (ListSelectionModel)e.getSource();
        if (!sourceSelectionModel.isSelectionEmpty()) {
          for (JList aList : myLists) {
            if (sourceSelectionModel != aList.getSelectionModel()) {
              aList.clearSelection();
            }
          }
        }
      }
    };

    for (final Object section : sections) {
      JPanel panel = new JPanel(new BorderLayout()) {// must be borderlayout for HideableDecorator to work
        @Override
        public Dimension getMaximumSize() {
          return new Dimension(super.getMaximumSize().width, super.getPreferredSize().height);
        }
      };
      String name = section.toString();
      HideableDecorator hidyPanel = new HideableDecorator(panel, name, false);

      FilteringListModel listModel = new FilteringListModel(new AbstractListModel() {
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

      //noinspection UndesirableClassUsage JBList does not work with HORIZONTAL_WRAP
      final JList list = new JList(listModel);
      list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      list.setVisibleRowCount(-1);
      list.getSelectionModel().addListSelectionListener(listSelectionListener);
      list.setName(name); // for tests to find the right list

      new ListSpeedSearch(list);

      myLists.add(list);
      myHideables.add(hidyPanel);
      hidyPanel.setContentComponent(list);
      add(panel);

    }
  }

  public void addListSelectionListener(@NotNull ListSelectionListener lsl) {
    for (JList list : myLists) {
      list.getSelectionModel().addListSelectionListener(lsl);
    }
  }

  public void setCellRenderer(@NotNull ListCellRenderer cellRenderer) {
    for (JList list : myLists) {
      list.setCellRenderer(cellRenderer);
    }
  }

  public void setFixedCellWidth(int width) {
    for (JList list : myLists) {
      list.setFixedCellWidth(width);
    }
  }

  public void setFixedCellHeight(int height) {
    for (JList list : myLists) {
      list.setFixedCellHeight(height);
    }
  }

  public void expandAll() {
    for (HideableDecorator hidyPanel : myHideables) {
      hidyPanel.setOn(true);
    }
  }

  public @Nullable Object getSelectedElement() {
    for (JList list : myLists) {
      if (list.getSelectedIndex() > -1) {
        return list.getSelectedValue();
      }
    }
    return null;
  }

  public void setSelectedElement(@Nullable Object selectedElement) {
    for (final JList list : myLists) {
      if (selectedElement == null) {
        list.clearSelection();
      }
      else {
        for (int i = 0; i < list.getModel().getSize(); i++) {
          if (list.getModel().getElementAt(i) == selectedElement) {
            list.setSelectedIndex(i);
            final int index = i;
            // we do this in invokeLater to make sure things like expandAll() have had their effect.
            SwingUtilities.invokeLater(new Runnable() {
              @Override
              public void run() {
                list.ensureIndexIsVisible(index);
              }
            });
            return;
          }
        }
      }
    }
  }

  @Override
  @SuppressWarnings("NonSynchronizedMethodOverridesSynchronizedMethod")
  public void addMouseListener(@NotNull MouseListener l) {
    for (JList list : myLists) {
      list.addMouseListener(l);
    }
  }

  @Override
  @SuppressWarnings("NonSynchronizedMethodOverridesSynchronizedMethod")
  public void removeMouseListener(@NotNull MouseListener l) {
    for (JList list : myLists) {
      list.removeMouseListener(l);
    }
  }

  public void setLayoutOrientation(int mode) {
    for (JList list : myLists) {
      list.setLayoutOrientation(mode);
    }
  }

  public void setFilter(@Nullable Condition condition) {
    for (JList list : myLists) {
      ((FilteringListModel)list.getModel()).setFilter(condition);
    }
  }
}
