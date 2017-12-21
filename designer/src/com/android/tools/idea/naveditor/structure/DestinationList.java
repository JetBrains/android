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

import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.adtui.workbench.*;
import com.android.tools.idea.common.model.*;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.adtui.common.ColoredIconGenerator;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.naveditor.model.NavComponentHelperKt;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.ColorSet;
import com.google.common.collect.ImmutableMap;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import icons.StudioIcons;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.*;
import java.util.List;

import static icons.StudioIcons.NavEditor.Tree.*;
import static com.android.SdkConstants.TAG_INCLUDE;
import static java.awt.event.KeyEvent.VK_BACK_SPACE;
import static java.awt.event.KeyEvent.VK_DELETE;
import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

/**
 * Left panel for the nav editor, showing a list of available destinations.
 */
public class DestinationList extends JPanel implements ToolContent<DesignSurface> {

  @VisibleForTesting
  static final String ROOT_NAME = "Root";

  @VisibleForTesting
  final DefaultListModel<NlComponent> myListModel = new DefaultListModel<>();
  private NavigationSchema mySchema;
  private ResourceResolver myResourceResolver;

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
  @VisibleForTesting
  public JLabel myBackLabel;
  @VisibleForTesting
  JPanel myBackPanel;
  private NavDesignSurface myDesignSurface;

  private static final Map<Icon, Icon> WHITE_ICONS = ImmutableMap.of(
    FRAGMENT, ColoredIconGenerator.INSTANCE.generateWhiteIcon(FRAGMENT),
    INCLUDE_GRAPH, ColoredIconGenerator.INSTANCE.generateWhiteIcon(INCLUDE_GRAPH),
    ACTIVITY, ColoredIconGenerator.INSTANCE.generateWhiteIcon(ACTIVITY),
    NESTED_GRAPH, ColoredIconGenerator.INSTANCE.generateWhiteIcon(NESTED_GRAPH));

  private DestinationList() {
    setLayout(new BorderLayout());
    myList = new JBList<>(myListModel);
    myList.setName("DestinationList");
    myList.setCellRenderer(new ColoredListCellRenderer<NlComponent>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList<? extends NlComponent> list,
                                           NlComponent component,
                                           int index,
                                           boolean isSelected,
                                           boolean cellHasFocus) {
        append(NavComponentHelperKt.getUiName(component, myResourceResolver));
        if (NavComponentHelperKt.isStartDestination(component)) {
          append(" - Start", SimpleTextAttributes.GRAY_ATTRIBUTES);
        }
        Icon icon = FRAGMENT;
        if (component.getTagName().equals(TAG_INCLUDE)) {
          icon = INCLUDE_GRAPH;
        }
        else if (NavComponentHelperKt.getDestinationType(component) == NavigationSchema.DestinationType.ACTIVITY) {
          icon = ACTIVITY;
        }
        else if (mySchema.getDestinationType(component.getTagName()) == NavigationSchema.DestinationType.NAVIGATION) {
          icon = NESTED_GRAPH;
        }
        if (isSelected) {
          icon = WHITE_ICONS.get(icon);
        }
        setIcon(icon);
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
  }

  @Override
  public void dispose() {
    mySelectionModel.removeListener(mySelectionModelListener);
    myModel.removeListener(myModelListener);
    myList.removeListSelectionListener(myListSelectionListener);
    myList.removeMouseListener(myMouseListener);
  }

  @Override
  public void setToolContext(@Nullable DesignSurface toolContext) {
    myDesignSurface = (NavDesignSurface)toolContext;
    if (mySelectionModel != null && mySelectionModelListener != null) {
      mySelectionModel.removeListener(mySelectionModelListener);
    }
    if (myListSelectionListener != null) {
      myList.removeListSelectionListener(myListSelectionListener);
    }
    if (myModel != null && myModelListener != null) {
      myModel.removeListener(myModelListener);
    }

    if (toolContext != null) {
      add(createBackPanel(toolContext), BorderLayout.NORTH);
      myModel = toolContext.getModel();
      mySelectionModel = toolContext.getSelectionModel();
      mySelectionModelListener = (model, selection) -> {
        if (mySelectionUpdating) {
          return;
        }
        try {
          mySelectionUpdating = true;
          Set<NlComponent> components = new HashSet<>(mySelectionModel.getSelection());
          List<Integer> selectedIndices = new ArrayList<>();
          for (int i = 0; i < myListModel.size(); i++) {
            if (components.contains(myListModel.get(i))) {
              selectedIndices.add(i);
            }
          }
          myList.setSelectedIndices(ArrayUtil.toIntArray(selectedIndices));

          updateBackLabel();
        }
        finally {
          mySelectionUpdating = false;
        }
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
        public void modelChanged(@NotNull NlModel model) {
          updateComponentList(toolContext);
        }

        @Override
        public void modelActivated(@NotNull NlModel model) {
          updateComponentList(toolContext);
        }

        @Override
        public void modelChangedOnLayout(@NotNull NlModel model, boolean animate) {
        }
      };
      myMouseListener = new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          if (e.getClickCount() == 2) {
            myDesignSurface.notifyComponentActivate(myList.getSelectedValue());
          }
        }
      };
      myList.addMouseListener(myMouseListener);
      myModel.addListener(myModelListener);

      Configuration configuration = toolContext.getConfiguration();
      assert configuration != null;
      myResourceResolver = configuration.getResourceResolver();

      ColorSet colorSet = SceneContext.get(toolContext.getCurrentSceneView()).getColorSet();
      myList.setBackground(colorSet.getSubduedBackground());
    }
    updateComponentList(toolContext);
  }

  private NavigationSchema getSchema() {
    if (mySchema == null) {
      assert myModel != null;
      mySchema = NavigationSchema.getOrCreateSchema(myModel.getFacet());
    }
    return mySchema;
  }

  private void updateBackLabel() {
    if (myModel.getComponents().contains(myDesignSurface.getCurrentNavigation())) {
      myBackPanel.setVisible(false);
    }
    else {
      myBackPanel.setVisible(true);
      NlComponent parent = myDesignSurface.getCurrentNavigation().getParent();
      // TODO: We are actually occasionally NPE-ing below, I think, though it should be impossible. Investigation is needed.
      myBackLabel.setText(parent.getParent() == null ? ROOT_NAME : NavComponentHelperKt.getUiName(parent, myResourceResolver));
    }
  }

  @NotNull
  private JComponent createBackPanel(@NotNull DesignSurface context) {
    myBackPanel = new JPanel(new BorderLayout());
    myBackLabel = new JLabel("", StudioIcons.Common.BACK_ARROW, SwingConstants.LEFT);
    ColorSet colorSet = SceneContext.get(context.getCurrentSceneView()).getColorSet();
    myBackPanel.setBackground(colorSet.getSubduedBackground());
    myBackPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, colorSet.getFrames()),
                                                             BorderFactory.createEmptyBorder(5, 5, 5, 5)));
    myBackPanel.setVisible(false);
    myBackPanel.add(myBackLabel, BorderLayout.WEST);
    myBackLabel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        goBack();
      }
    });
    return myBackPanel;
  }

  @VisibleForTesting
  void goBack() {
    //noinspection ConstantConditions This is only shown in the case where the current navigation has a parent.
    myDesignSurface.setCurrentNavigation(myDesignSurface.getCurrentNavigation().getParent());
    updateComponentList(myDesignSurface);
    updateBackLabel();
  }

  private void updateComponentList(@Nullable DesignSurface toolContext) {
    List<NlComponent> newElements = new ArrayList<>();
    if (toolContext != null) {
      NlComponent root = myDesignSurface.getCurrentNavigation();
      for (NlComponent child : root.getChildren()) {
        if (getSchema().getDestinationType(child.getTagName()) != null) {
          newElements.add(child);
        }
      }
    }
    if (!newElements.equals(Collections.list(myListModel.elements()))) {
      myListModel.clear();
      newElements.forEach(myListModel::addElement);
    }
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return this;
  }

  public static class DestinationListDefinition extends ToolWindowDefinition<DesignSurface> {
    public DestinationListDefinition() {
      super("Destinations", AllIcons.Toolwindows.ToolWindowHierarchy, "destinations", Side.LEFT, Split.TOP, AutoHide.DOCKED,
            DestinationList::new);
    }
  }
}
