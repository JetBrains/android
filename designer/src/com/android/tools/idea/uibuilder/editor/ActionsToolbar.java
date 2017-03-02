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
package com.android.tools.idea.uibuilder.editor;

import com.android.tools.idea.assistant.view.UIUtils;
import com.android.tools.idea.configurations.ConfigurationHolder;
import com.android.tools.idea.uibuilder.model.ModelListener;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.model.SelectionModel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.DesignSurfaceListener;
import com.android.tools.idea.uibuilder.surface.PanZoomListener;
import com.android.tools.idea.uibuilder.surface.SceneView;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AdjustmentEvent;
import java.util.Collections;
import java.util.List;

/**
 * The actions toolbar updates dynamically based on the component selection, their
 * parents (and if no selection, the root layout)
 */
abstract public class ActionsToolbar implements DesignSurfaceListener, ModelListener {
  protected final DesignSurface mySurface;
  private NlModel myModel;
  private JComponent myToolbarComponent;
  protected ActionToolbar myActionToolbar;
  private final DefaultActionGroup myDynamicGroup = new DefaultActionGroup();

  public ActionsToolbar(DesignSurface surface) {
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
    bottom.add(layoutToolBar.getComponent(), BorderLayout.CENTER);

    ActionToolbar zoomToolBar = actionManager.createActionToolbar("NlRhsToolbar", getRhsActions(mySurface), true);
    zoomToolBar.getComponent().setName("NlRhsToolbar");
    bottom.add(zoomToolBar.getComponent(), BorderLayout.EAST);

    panel.add(bottom, BorderLayout.SOUTH);

    mySurface.addListener(this);
    mySurface.addPanZoomListener(new PanZoomListener() {
      @Override
      public void zoomChanged(DesignSurface designSurface) {
        zoomToolBar.updateActionsImmediately();
      }

      @Override
      public void panningChanged(AdjustmentEvent adjustmentEvent) {

      }
    });

    updateActions();

    return panel;
  }

  abstract protected DefaultActionGroup createConfigActions(ConfigurationHolder configurationHolder, DesignSurface surface);
  abstract protected ActionGroup getRhsActions(DesignSurface surface);

  public void updateActions() {
    SceneView view = mySurface.getCurrentSceneView();
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
      updateActions(selection);
    }
  }

  public void setModel(NlModel model) {
    myModel = model;
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

  protected void updateActions(@NotNull List<NlComponent> newSelection) {
    SceneView screenView = mySurface.getCurrentSceneView();
    if (screenView == null) {
      return;
    }

    boolean isSupportedByDesigner = mySurface.getLayoutType().isSupportedByDesigner();
    UIUtil.invokeLaterIfNeeded(() -> myActionToolbar.getComponent().setVisible(isSupportedByDesigner));

    // TODO: Perform caching
    myDynamicGroup.removeAll();
    NlComponent parent = findSharedParent(newSelection);
    if (parent != null) {
      mySurface.getActionManager().addActions(myDynamicGroup, null, parent, newSelection, true);
    }
  }

  // ---- Implements DesignSurfaceListener ----

  @Override
  public void componentSelectionChanged(@NotNull DesignSurface surface, @NotNull List<NlComponent> newSelection) {
    assert surface == mySurface;
    if (!newSelection.isEmpty()) {
      updateActions(newSelection);
    } else {
      updateActions();
    }
  }

  @Override
  public void sceneChanged(@NotNull DesignSurface surface, @Nullable SceneView sceneView) {
    // The toolbar depends on the current ScreenView for its content,
    // so reload when the ScreenView changes.
    myActionToolbar.updateActionsImmediately();
    updateActions();
  }

  @Override
  public void modelChanged(@NotNull DesignSurface surface, @Nullable NlModel model) {
    if (myDynamicGroup.getChildrenCount() == 0) {
      myModel = model;
      updateActions();
    }
  }

  @Override
  public boolean activatePreferredEditor(@NotNull DesignSurface surface, @NotNull NlComponent component) {
    return false;
  }

  // ---- Implements ModelListener ----

  @Override
  public void modelChanged(@NotNull NlModel model) {
  }

  @Override
  public void modelRendered(@NotNull NlModel model) {
    // Ensure that the toolbar is populated initially
    updateActions();
    model.removeListener(this);
  }

  @Override
  public void modelChangedOnLayout(@NotNull NlModel model, boolean animate) {
    // Do nothing
  }

}
