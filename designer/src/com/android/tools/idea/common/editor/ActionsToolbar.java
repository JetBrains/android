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
package com.android.tools.idea.common.editor;

import com.android.tools.idea.common.model.ModelListener;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.DesignSurfaceListener;
import com.android.tools.idea.common.surface.PanZoomListener;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationListener;
import com.android.tools.idea.uibuilder.scene.RenderListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.util.ui.JBUI;
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
public final class ActionsToolbar implements DesignSurfaceListener, Disposable, ModelListener, PanZoomListener, ConfigurationListener,
                                             RenderListener {

  private static final int CONFIGURATION_UPDATE_FLAGS = ConfigurationListener.CFG_TARGET |
                                                        ConfigurationListener.CFG_DEVICE;

  private final DesignSurface mySurface;
  private NlModel myModel;
  private JComponent myToolbarComponent;
  private ActionToolbar myNorthToolbar;
  private ActionToolbar myNorthEastToolbar;
  private final DefaultActionGroup myDynamicGroup = new DefaultActionGroup();

  public ActionsToolbar(@Nullable Disposable parent, DesignSurface surface) {
    if (parent != null) {
      Disposer.register(parent, this);
    }
    mySurface = surface;
  }

  @Override
  public void dispose() {
    myModel.removeListener(this);

    mySurface.removePanZoomListener(this);
    mySurface.removeListener(this);
    Configuration configuration = mySurface.getConfiguration();
    if (configuration != null) {
      configuration.removeListener(this);
    }
  }

  @NotNull
  public JComponent getToolbarComponent() {
    if (myToolbarComponent == null) {
      myToolbarComponent = createToolbarComponent();

      mySurface.addListener(this);
      mySurface.addPanZoomListener(this);
      Configuration configuration;
      if ((configuration = mySurface.getConfiguration()) != null) {
        configuration.addListener(this);
      }

      updateActions();
    }

    return myToolbarComponent;
  }

  @NotNull
  private JComponent createToolbarComponent() {
    ToolbarActionGroups groups = mySurface.getLayoutType().getToolbarActionGroups(mySurface);

    myNorthToolbar = createActionToolbar("NlConfigToolbar", groups.getNorthGroup());
    myNorthToolbar.setLayoutPolicy(ActionToolbar.AUTO_LAYOUT_POLICY);

    JComponent northToolbarComponent = myNorthToolbar.getComponent();
    northToolbarComponent.setName("NlConfigToolbar");
    northToolbarComponent.setBorder(JBUI.Borders.empty());

    myNorthEastToolbar = createActionToolbar("NlRhsConfigToolbar", groups.getNorthEastGroup());

    JComponent northEastToolbarComponent = myNorthEastToolbar.getComponent();
    northEastToolbarComponent.setName("NlRhsConfigToolbar");

    ActionToolbar centerToolbar = createActionToolbar("NlLayoutToolbar", myDynamicGroup);
    centerToolbar.setLayoutPolicy(ActionToolbar.AUTO_LAYOUT_POLICY);

    JComponent centerToolbarComponent = centerToolbar.getComponent();
    centerToolbarComponent.setName("NlLayoutToolbar");
    centerToolbarComponent.setBorder(JBUI.Borders.empty());

    ActionToolbar eastToolbar = createActionToolbar("NlRhsToolbar", groups.getEastGroup());

    JComponent eastToolbarComponent = eastToolbar.getComponent();
    eastToolbarComponent.setName("NlRhsToolbar");
    eastToolbarComponent.setBorder(JBUI.Borders.empty());

    JComponent northPanel = new JPanel(new BorderLayout());
    northPanel.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
    northPanel.add(northToolbarComponent, BorderLayout.CENTER);
    northPanel.add(northEastToolbarComponent, BorderLayout.EAST);

    JComponent panel = new JPanel(new BorderLayout());
    panel.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));

    panel.add(northPanel, BorderLayout.NORTH);
    panel.add(centerToolbarComponent, BorderLayout.CENTER);
    panel.add(eastToolbarComponent, BorderLayout.EAST);

    return panel;
  }

  @NotNull
  private static ActionToolbar createActionToolbar(@NotNull String place, @NotNull ActionGroup group) {
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(place, group, true);
    toolbar.setLayoutPolicy(ActionToolbar.WRAP_LAYOUT_POLICY);

    return toolbar;
  }

  public void updateActions() {
    SceneView view = mySurface.getCurrentSceneView();
    if (view != null) {
      SelectionModel selectionModel = view.getSelectionModel();
      List<NlComponent> selection = selectionModel.getSelection();
      if (selection.isEmpty()) {
        List<NlComponent> roots = view.getModel().getComponents();
        if (roots.size() == 1) {
          selection = Collections.singletonList(roots.get(0));
        }
        else {
          // Model not yet rendered: when it's done, update. Listener is removed as soon as palette fires from listener callback.
          SceneManager manager = mySurface.getSceneManager();
          if (manager != null) {
            manager.addRenderListener(this);
          }
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
      }
      else if (parent != selected.getParent()) {
        parent = null;
        break;
      }
    }
    return parent;
  }

  private void updateActions(@NotNull List<NlComponent> newSelection) {
    SceneView screenView = mySurface.getCurrentSceneView();
    if (screenView == null) {
      return;
    }

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
    }
    else {
      updateActions();
    }
  }

  @Override
  public void sceneChanged(@NotNull DesignSurface surface, @Nullable SceneView sceneView) {
    // The toolbar depends on the current ScreenView for its content,
    // so reload when the ScreenView changes.
    myNorthToolbar.updateActionsImmediately();
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

  // ---- Implements RenderListener ----
  @Override
  public void onRenderCompleted() {
    // Ensure that the toolbar is populated initially
    updateActions();
    SceneManager manager = mySurface.getSceneManager();
    if (manager != null) {
      manager.removeRenderListener(this);
    }
  }

  // ---- Implements ModelListener ----

  @Override
  public void modelChangedOnLayout(@NotNull NlModel model, boolean animate) {
    // Do nothing
  }

  @Override
  public void zoomChanged(@NotNull DesignSurface surface) {
    myNorthEastToolbar.updateActionsImmediately();
  }

  @Override
  public void panningChanged(@NotNull AdjustmentEvent event) {
    myNorthEastToolbar.updateActionsImmediately();
  }

  @Override
  public boolean changed(int flags) {
    if ((flags & CONFIGURATION_UPDATE_FLAGS) > 0) {
      if (myNorthEastToolbar != null) {
        // the North toolbar is the one holding the Configuration Actions
        myNorthEastToolbar.updateActionsImmediately();
      }
    }
    return true;
  }
}
