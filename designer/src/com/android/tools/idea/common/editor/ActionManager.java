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

import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.SceneView;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * Provides and handles actions for an {@link NlEditor}.
 */
public abstract class ActionManager<S extends DesignSurface> {
  protected final S mySurface;

  public ActionManager(@NotNull S surface) {
    mySurface = surface;
  }

  public abstract void registerActionsShortcuts(@NotNull JComponent component);

  protected static void registerAction(@NotNull AnAction action, @NonNls String actionId, @NotNull JComponent component) {
    action.registerCustomShortcutSet(
      com.intellij.openapi.actionSystem.ActionManager.getInstance().getAction(actionId).getShortcutSet(),
      component
    );
  }

  @NotNull
  public JComponent createToolbar() {
    ActionsToolbar actionsToolbar = createActionsToolbar();
    return actionsToolbar.getToolbarComponent();
  }

  @NotNull
  protected ActionsToolbar createActionsToolbar() {
    return new ActionsToolbar(mySurface, mySurface);
  }

  public void showPopup(@NotNull MouseEvent event) {
    NlComponent component = null;
    int x = event.getX();
    int y = event.getY();
    SceneView sceneView = mySurface.getSceneView(x, y);
    if (sceneView == null) {
      sceneView = mySurface.getCurrentSceneView();
    }
    if (sceneView != null) {
      component = Coordinates.findComponent(sceneView, x, y);
    }
    showPopup(event, component);
  }

  public void showPopup(@NotNull MouseEvent event, @Nullable NlComponent leafComponent) {
    com.intellij.openapi.actionSystem.ActionManager actionManager = com.intellij.openapi.actionSystem.ActionManager.getInstance();

    DefaultActionGroup group = createPopupMenu(actionManager, leafComponent);
    ActionPopupMenu popupMenu = actionManager.createActionPopupMenu("LayoutEditor", group);
    Component invoker = event.getSource() instanceof Component ? (Component)event.getSource() : mySurface;
    popupMenu.getComponent().show(invoker, event.getX(), event.getY());
  }

  @NotNull
  abstract protected DefaultActionGroup createPopupMenu(@NotNull com.intellij.openapi.actionSystem.ActionManager actionManager,
                                                        @Nullable NlComponent leafComponent);

  public abstract void addActions(@NotNull DefaultActionGroup group, @Nullable NlComponent component, @Nullable NlComponent parent,
                                  @NotNull java.util.List<NlComponent> newSelection, boolean toolbar);
}
