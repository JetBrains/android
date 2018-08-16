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

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.surface.DesignSurface;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ShortcutSet;
import java.awt.Component;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides and handles actions for an {@link NlEditor}.
 */
public abstract class ActionManager<S extends DesignSurface> {
  protected final S mySurface;

  public ActionManager(@NotNull S surface) {
    mySurface = surface;
  }

  /**
   * Register keyboard shortcuts onto the provided component.
   *
   * @param component        The component onto which shortcut should be registered.
   * @param parentDisposable A disposable used to unregister the actions. If the parameter is null but
   *                         component is a {@link Disposable}, component will be used as the parent disposable.
   */
  public abstract void registerActionsShortcuts(@NotNull JComponent component,
                                                @Nullable Disposable parentDisposable);

  protected void registerAction(@NotNull AnAction action,
                                @NonNls String actionId,
                                @NotNull JComponent component,
                                @Nullable Disposable parentDisposable) {
    registerAction(action,
                   com.intellij.openapi.actionSystem.ActionManager.getInstance().getAction(actionId).getShortcutSet(),
                   component,
                   parentDisposable
    );
  }

  protected void registerAction(@NotNull AnAction action,
                                @NotNull KeyStroke keyStroke,
                                @NotNull JComponent component,
                                @Nullable Disposable parentDisposable) {
    registerAction(action, new CustomShortcutSet(keyStroke), component, parentDisposable);
  }

  protected void registerAction(@NotNull AnAction action,
                                @NotNull ShortcutSet shortcutSet,
                                @NotNull JComponent component,
                                @Nullable Disposable parentDisposable) {
    Disposable disposable;
    if (parentDisposable != null) {
      disposable = parentDisposable;
    }
    else if (component instanceof Disposable) {
      disposable = (Disposable)component;
    }
    else {
      disposable = mySurface;
    }
    action.registerCustomShortcutSet(shortcutSet, component, disposable);
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

  public void showPopup(@NotNull MouseEvent event, @Nullable NlComponent leafComponent) {
    com.intellij.openapi.actionSystem.ActionManager actionManager = com.intellij.openapi.actionSystem.ActionManager.getInstance();

    DefaultActionGroup group = createPopupMenu(actionManager, leafComponent);
    ActionPopupMenu popupMenu = actionManager.createActionPopupMenu("LayoutEditor", group);
    Component invoker = event.getSource() instanceof Component ? (Component)event.getSource() : mySurface;
    popupMenu.getComponent().show(invoker, event.getX(), event.getY());
  }

  @VisibleForTesting
  @NotNull
  public abstract DefaultActionGroup createPopupMenu(@NotNull com.intellij.openapi.actionSystem.ActionManager actionManager,
                                                        @Nullable NlComponent leafComponent);

  public abstract void addActions(@NotNull DefaultActionGroup group, @Nullable NlComponent component,
                                  @NotNull List<NlComponent> newSelection, boolean toolbar);
}
