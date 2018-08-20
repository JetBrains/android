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
package com.android.tools.idea.naveditor.structure;

import com.android.annotations.VisibleForTesting;
import com.android.tools.adtui.common.ColoredIconGenerator;
import com.android.tools.adtui.workbench.StartFilteringListener;
import com.android.tools.idea.common.model.*;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.naveditor.model.NavComponentHelperKt;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.ColorSet;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.ui.speedSearch.FilteringListModel;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

import static icons.StudioIcons.NavEditor.Tree.*;
import static java.awt.event.KeyEvent.*;
import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

/**
 * Left panel for the nav editor, showing a list of available destinations.
 */
public class DestinationList extends JPanel implements DataProvider, Disposable {

  @VisibleForTesting
  static final String ROOT_NAME = "Root";

  @VisibleForTesting
  final DefaultListModel<NlComponent> myUnderlyingModel = new DefaultListModel<>();
  @VisibleForTesting
  final FilteringListModel<NlComponent> myListModel = new FilteringListModel<>(myUnderlyingModel);
  private NavigationSchema mySchema;

  @VisibleForTesting
  SelectionModel mySelectionModel;

  @VisibleForTesting
  public final JBList<NlComponent> myList;
  private boolean mySelectionUpdating;
  private SelectionListener mySelectionModelListener;
  private ModelListener myModelListener;
  private NlModel myModel;
  private ListSelectionListener myListSelectionListener;
  private MouseListener myMouseListener;
  private NavDesignSurface myDesignSurface;

  private StartFilteringListener myStartFilteringListener;
  private Runnable myStopFilteringListener;

  private static final Map<Icon, Icon> WHITE_ICONS = ImmutableMap.of(
    FRAGMENT, ColoredIconGenerator.INSTANCE.generateWhiteIcon(FRAGMENT),
    INCLUDE_GRAPH, ColoredIconGenerator.INSTANCE.generateWhiteIcon(INCLUDE_GRAPH),
    ACTIVITY, ColoredIconGenerator.INSTANCE.generateWhiteIcon(ACTIVITY),
    NESTED_GRAPH, ColoredIconGenerator.INSTANCE.generateWhiteIcon(NESTED_GRAPH));

  DestinationList(@NotNull Disposable parentDisposable, @NotNull NavDesignSurface surface) {
    myDesignSurface = surface;
    Disposer.register(parentDisposable, this);
    setLayout(new BorderLayout());
    myList = new JBList<>(myListModel);
    myList.getEmptyText().setText("");
    myList.setName("DestinationList");
    myList.setCellRenderer(new ColoredListCellRenderer<NlComponent>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList<? extends NlComponent> list,
                                           NlComponent component,
                                           int index,
                                           boolean isSelected,
                                           boolean cellHasFocus) {
        if (isSelected && !cellHasFocus) {
          setBackground(UIUtil.getListUnfocusedSelectionBackground());
          mySelectionForeground = UIUtil.getListForeground();
        }
        append(NavComponentHelperKt.getUiName(component));
        if (NavComponentHelperKt.isStartDestination(component)) {
          append(" - Start", SimpleTextAttributes.GRAY_ATTRIBUTES);
        }
        Icon icon = FRAGMENT;
        if (NavComponentHelperKt.isInclude(component)) {
          icon = INCLUDE_GRAPH;
        }
        else if (NavComponentHelperKt.isActivity(component)) {
          icon = ACTIVITY;
        }
        else if (NavComponentHelperKt.isNavigation(component)) {
          icon = NESTED_GRAPH;
        }
        if (isSelected && cellHasFocus) {
          icon = WHITE_ICONS.get(icon);
        }
        setIcon(icon);
      }
    });
    myList.addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped(KeyEvent e) {
        if (Character.isAlphabetic(e.getKeyChar()) && myStartFilteringListener != null) {
          myStartFilteringListener.startFiltering(e.getKeyChar());
          e.consume();
        }
        if (e.getKeyChar() == VK_ESCAPE && myStopFilteringListener != null) {
          myStopFilteringListener.run();
        }
      }
    });
    InputMap inputMap = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    final String deleteDestinationKey = "deleteDestination";
    inputMap.put(KeyStroke.getKeyStroke(VK_DELETE, 0), deleteDestinationKey);
    inputMap.put(KeyStroke.getKeyStroke(VK_BACK_SPACE, 0), deleteDestinationKey);
    getActionMap().put(deleteDestinationKey, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent event) {
          List<NlComponent> toDelete = myList.getSelectedValuesList();
          if (!toDelete.isEmpty()) {
            new WriteCommandAction(myDesignSurface.getProject(), "Delete Destination" + (toDelete.size() > 1 ? "s" : ""),
                                   myDesignSurface.getModel().getFile()) {
              @Override
              protected void run(@NotNull Result result) {
                myDesignSurface.getModel().delete(toDelete);
              }
            }.execute();
          }
      }
    });

    JScrollPane pane = ScrollPaneFactory.createScrollPane(myList, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_AS_NEEDED);
    pane.setBorder(null);
    add(pane, BorderLayout.CENTER);

    myModel = surface.getModel();
    mySelectionModel = surface.getSelectionModel();
    mySelectionModelListener = (model, selection) -> {
      updateListSelection();
    };
    mySelectionModel.addListener(mySelectionModelListener);
    myListSelectionListener = e -> {
      if (mySelectionUpdating || e.getValueIsAdjusting()) {
        return;
      }
      try {
        mySelectionUpdating = true;
        mySelectionModel.setSelection(myList.getSelectedValuesList());
        myDesignSurface.scrollToCenter(myList.getSelectedValuesList());
      }
      finally {
        mySelectionUpdating = false;
      }
    };
    myList.addListSelectionListener(myListSelectionListener);
    myModelListener = new ModelListener() {
      @Override
      public void modelDerivedDataChanged(@NotNull NlModel model) {
        updateComponentList();
      }

      @Override
      public void modelActivated(@NotNull NlModel model) {
        updateComponentList();
      }

      @Override
      public void modelChangedOnLayout(@NotNull NlModel model, boolean animate) {
      }
    };
    myMouseListener = new DestinationListMouseListener();
    myList.addMouseListener(myMouseListener);
    myModel.addListener(myModelListener);

    ColorSet colorSet = SceneContext.get(surface.getCurrentSceneView()).getColorSet();
    myList.setBackground(colorSet.getSubduedBackground());
    updateComponentList();
  }

  @Override
  public void dispose() {
    mySelectionModel.removeListener(mySelectionModelListener);
    myModel.removeListener(myModelListener);
    myList.removeListSelectionListener(myListSelectionListener);
    myList.removeMouseListener(myMouseListener);
  }

  private NavigationSchema getSchema() {
    if (mySchema == null) {
      assert myModel != null;
      mySchema = NavigationSchema.get(myModel.getModule());
    }
    return mySchema;
  }

  void updateComponentList() {
    List<NlComponent> newElements = new ArrayList<>();
    NlComponent root = myDesignSurface.getCurrentNavigation();
    for (NlComponent child : root.getChildren()) {
      if (NavComponentHelperKt.isDestination(child)) {
        newElements.add(child);
      }
    }
    if (!newElements.equals(Collections.list(myUnderlyingModel.elements()))) {
      mySelectionUpdating = true;

      try {
        myUnderlyingModel.clear();
        newElements.forEach(myUnderlyingModel::addElement);
      }
      finally {
        mySelectionUpdating = false;
      }
    }
    updateListSelection();
  }

  private void updateListSelection() {
    if (mySelectionUpdating) {
      return;
    }
    try {
      mySelectionUpdating = true;
      Set<NlComponent> components = new HashSet<>(mySelectionModel.getSelection());
      List<Integer> selectedIndices = new ArrayList<>();
      for (int i = 0; i < myUnderlyingModel.size(); i++) {
        if (components.contains(myUnderlyingModel.get(i))) {
          selectedIndices.add(i);
        }
      }
      myList.setSelectedIndices(ArrayUtil.toIntArray(selectedIndices));
    }
    finally {
      mySelectionUpdating = false;
    }
  }

  public void setFilter(@NotNull String filter) {
    myListModel.setFilter(c -> NavComponentHelperKt.getUiName(c).toLowerCase(getLocale()).contains(filter.toLowerCase(getLocale())));
  }

  public void setStartFiltering(@NotNull StartFilteringListener listener) {
    myStartFilteringListener = listener;
  }

  public void setStopFiltering(@NotNull Runnable stopFilteringListener) {
    myStopFilteringListener = stopFilteringListener;
  }

  // ---- Implements DataProvider ----
  @Override
  public Object getData(@NonNls String dataId) {
    return myDesignSurface == null ? null : myDesignSurface.getData(dataId);
  }

  private class DestinationListMouseListener extends MouseAdapter {
    @Override
    public void mouseClicked(MouseEvent e) {
      if (e.getClickCount() == 2) {
        handleDoubleClick(e);
      }
      else {
        handlePopup(e);
      }
    }

    @Override
    public void mousePressed(MouseEvent e) {
      handlePopup(e);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      handlePopup(e);
    }

    private void handlePopup(MouseEvent e) {
      if (e.isPopupTrigger()) {
        int index = myList.locationToIndex(e.getPoint());
        if (index != -1) {
          NlComponent component = myList.getModel().getElementAt(index);
          myDesignSurface.getActionManager().showPopup(e, component);
        }
      }
    }

    private void handleDoubleClick(@NotNull MouseEvent event) {
      int index = myList.locationToIndex(event.getPoint());
      if (index != -1) {
        NlComponent component = myList.getModel().getElementAt(index);
        myDesignSurface.notifyComponentActivate(component);
      }
    }
  }
}
