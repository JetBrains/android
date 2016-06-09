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
package com.android.tools.idea.uibuilder.editor;

import com.android.tools.idea.configurations.*;
import com.android.tools.idea.uibuilder.actions.LintNotificationAction;
import com.android.tools.idea.uibuilder.actions.SetZoomAction;
import com.android.tools.idea.uibuilder.actions.TogglePanningDialogAction;
import com.android.tools.idea.uibuilder.actions.ZoomLabelAction;
import com.android.tools.idea.uibuilder.model.ModelListener;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.model.SelectionModel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.DesignSurfaceListener;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.tools.idea.uibuilder.surface.ZoomType;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

/**
 * The actions toolbar updates dynamically based on the component selection, their
 * parents (and if no selection, the root layout)
 */
public class NlActionsToolbar implements DesignSurfaceListener, ModelListener {
  private final DesignSurface mySurface;
  private NlModel myModel;
  private JComponent myToolbarComponent;
  private final DefaultActionGroup myDynamicGroup = new DefaultActionGroup();
  private ActionToolbar myActionToolbar;

  public NlActionsToolbar(@NotNull DesignSurface surface) {
    mySurface = surface;
  }

  @NotNull
  public JComponent getToolbarComponent() {
    if (myToolbarComponent == null) {
      myToolbarComponent = createToolbarComponent();
    }

    return myToolbarComponent;
  }

  private JComponent createToolbarComponent() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));

    // Create a layout where there are three toolbars:
    // +----------------------------------------------------------------------------+
    // | Normal toolbar, minus dynamic actions                                      |
    // +---------------------------------------------+------------------------------+
    // | Dynamic layout actions                      | Zoom actions and file status |
    // +---------------------------------------------+------------------------------+
    ConfigurationHolder context = new NlEditorPanel.NlConfigurationHolder(mySurface);
    ActionGroup configGroup = createConfigActions(context, mySurface);

    ActionManager actionManager = ActionManager.getInstance();
    myActionToolbar = actionManager.createActionToolbar("NlConfigToolbar", configGroup, true);
    myActionToolbar.getComponent().setName("NlConfigToolbar");
    myActionToolbar.setLayoutPolicy(ActionToolbar.WRAP_LAYOUT_POLICY);
    JComponent actionToolbarComponent = myActionToolbar.getComponent();
    actionToolbarComponent.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
    panel.add(actionToolbarComponent, BorderLayout.NORTH);

    final ActionToolbar layoutToolBar = actionManager.createActionToolbar("NlLayoutToolbar", myDynamicGroup, true);
    layoutToolBar.getComponent().setName("NlLayoutToolbar");
    layoutToolBar.setLayoutPolicy(ActionToolbar.WRAP_LAYOUT_POLICY);

    JPanel bottom = new JPanel(new BorderLayout());
    bottom.add(layoutToolBar.getComponent(), BorderLayout.WEST);

    JPanel combined = new JPanel(new BorderLayout());
    ActionToolbar zoomToolBar = actionManager.createActionToolbar("NlRhsToolbar", getRhsActions(mySurface), true);
    combined.add(zoomToolBar.getComponent(), BorderLayout.WEST);
    bottom.add(combined, BorderLayout.EAST);

    panel.add(bottom, BorderLayout.SOUTH);

    mySurface.addListener(this);

    updateViewActions();

    return panel;
  }

  private static ActionGroup getRhsActions(DesignSurface surface) {
    DefaultActionGroup group = new DefaultActionGroup();

    group.add(new SetZoomAction(surface, ZoomType.OUT));
    group.add(new ZoomLabelAction(surface));
    group.add(new SetZoomAction(surface, ZoomType.IN));
    group.add(new SetZoomAction(surface, ZoomType.FIT));
    group.add(new TogglePanningDialogAction(surface));
    group.addSeparator();
    group.add(new LintNotificationAction(surface));

    return group;
  }

  private static DefaultActionGroup createConfigActions(ConfigurationHolder configurationHolder, DesignSurface surface) {
    DefaultActionGroup group = new DefaultActionGroup();

    OrientationMenuAction orientationAction = new OrientationMenuAction(configurationHolder);
    group.add(orientationAction);
    group.addSeparator();

    DeviceMenuAction deviceAction = new DeviceMenuAction(configurationHolder);
    group.add(deviceAction);

    TargetMenuAction targetMenuAction = new TargetMenuAction(configurationHolder);
    group.add(targetMenuAction);

    ThemeMenuAction themeAction = new ThemeMenuAction(configurationHolder);
    group.add(themeAction);

    LocaleMenuAction localeAction = new LocaleMenuAction(configurationHolder);
    group.add(localeAction);

    group.addSeparator();
    group.add(new DesignModeAction(surface));
    group.add(new BlueprintModeAction(surface));

    ConfigurationMenuAction configAction = new ConfigurationMenuAction(surface);
    group.add(configAction);

    return group;
  }

  @Nullable
  private static NlComponent findSharedParent(@NotNull List<NlComponent> newSelection) {
    NlComponent parent = null;
    for (NlComponent selected : newSelection) {
      if (parent == null) {
        parent = selected.getParent();
        if (newSelection.size() == 1 && selected.isRoot() && (parent == null || parent.isRoot())) {
          // If you select a root layout, offer selection actions on it as well
          return selected;
        }
      } else if (parent != selected.getParent()) {
        parent = null;
        break;
      }
    }
    return parent;
  }

  public void updateViewActions() {
    ScreenView view = mySurface.getCurrentScreenView();
    if (view != null) {
      SelectionModel selectionModel = view.getSelectionModel();
      List<NlComponent> selection = selectionModel.getSelection();
      if (selection.isEmpty()) {
        List<NlComponent> roots = view.getModel().getComponents();
        if (roots.size() == 1) {
          selection = Collections.singletonList(roots.get(0));
        } else {
          // Model not yet rendered: when it's done, update. Listener is removed as soon as palette fires from listener callback.
          myModel.addListener(this);
          return;
        }
      }
      updateViewActions(selection);
    }
  }

  private void updateViewActions(@NotNull List<NlComponent> newSelection) {
    ScreenView screenView = mySurface.getCurrentScreenView();
    if (screenView == null) {
      return;
    }

    // TODO: Perform caching
    myDynamicGroup.removeAll();
    NlComponent parent = findSharedParent(newSelection);
    if (parent != null) {
      mySurface.getActionManager().addViewActions(myDynamicGroup, null, parent, newSelection, true);
    }
  }

  public void setModel(NlModel model) {
    myModel = model;
  }

  // ---- Implements DesignSurfaceListener ----

  @Override
  public void componentSelectionChanged(@NotNull DesignSurface surface, @NotNull List<NlComponent> newSelection) {
    assert surface == mySurface;
    if (!newSelection.isEmpty()) {
      updateViewActions(newSelection);
    } else {
      updateViewActions();
    }
  }

  @Override
  public void screenChanged(@NotNull DesignSurface surface, @Nullable ScreenView screenView) {
    // The toolbar depends on the current ScreenView for its content,
    // so reload when the ScreenView changes.
    myActionToolbar.updateActionsImmediately();
    updateViewActions();
  }

  @Override
  public void modelChanged(@NotNull DesignSurface surface, @Nullable NlModel model) {
    if (myDynamicGroup.getChildrenCount() == 0) {
      myModel = model;
      updateViewActions();
    }
  }

  // ---- Implements ModelListener ----

  @Override
  public void modelChanged(@NotNull NlModel model) {
  }

  @Override
  public void modelRendered(@NotNull NlModel model) {
    // Ensure that the toolbar is populated initially
    updateViewActions();
    model.removeListener(this);
  }
}
