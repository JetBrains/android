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

import com.android.tools.adtui.stdui.KeyBindingKt;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.LabelPanel;
import com.android.tools.idea.common.surface.LayoutData;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.common.surface.SceneViewErrorsPanel;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManagerUtilsKt;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides and handles actions for a {@link DesignerEditor}.
 */
public abstract class ActionManager<S extends DesignSurface<?>> {
  protected final S mySurface;

  @Nullable
  private JComponent surfaceToolbar;

  protected ActionManager(@NotNull S surface) {
    mySurface = surface;
  }

  protected static void registerAction(@NotNull AnAction action,
                                       @NonNls String actionId,
                                       @NotNull JComponent component) {
    Arrays.stream(com.intellij.openapi.actionSystem.ActionManager.getInstance().getAction(actionId).getShortcutSet().getShortcuts())
      .filter(shortcut -> shortcut instanceof KeyboardShortcut && ((KeyboardShortcut)shortcut).getSecondKeyStroke() == null)
      .forEach(shortcut -> registerAction(action, ((KeyboardShortcut)shortcut).getFirstKeyStroke(), component));
  }

  protected static void registerAction(@NotNull AnAction action,
                                       @NotNull KeyStroke keyStroke,
                                       @NotNull JComponent component) {
    KeyBindingKt.registerAnActionKey(component,
                                     () -> action, keyStroke,
                                     action.getClass().getSimpleName(),
                                     JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
  }

  @NotNull
  public JComponent createToolbar() {
    return new ActionsToolbar(mySurface, mySurface).getToolbarComponent();
  }

  /**
   * Get the created surface toolbar. If the toolbar is not created, then it is created and returned.
   */
  @NotNull
  public JComponent getDesignSurfaceToolbar() {
    if (surfaceToolbar == null) {
      surfaceToolbar = new DesignSurfaceFloatingActionsToolbarProvider(mySurface, mySurface, mySurface).getFloatingToolbar();
    }
    return surfaceToolbar;
  }

  /**
   * Returns a pre-registered action for the given action name. See {@link com.intellij.openapi.actionSystem.IdeActions}
   */
  @Nullable
  protected static AnAction getRegisteredActionByName(@NotNull String actionName) {
    return com.intellij.openapi.actionSystem.ActionManager.getInstance().getAction(actionName);
  }


  /**
   * Register keyboard shortcuts onto the provided component.
   *
   * @param component The component onto which shortcut should be registered.
   */
  public abstract void registerActionsShortcuts(@NotNull JComponent component);

  /**
   * Creates the actions for the pop-up menu (a.k.a. context menu) for the given {@link NlComponent}.
   *
   * @param leafComponent The target component for the pop-up menu (e.g. The right-clicked component)
   */
  @NotNull
  public abstract DefaultActionGroup getPopupMenuActions(@Nullable NlComponent leafComponent);

  /**
   * Creates the actions for the given {@link NlComponent}s.
   *
   * @param selection The selected {@link NlComponent}s in {@link DesignSurface}.
   */
  @NotNull
  public abstract DefaultActionGroup getToolbarActions(@NotNull List<NlComponent> selection);

  /**
   * Returns the actions for the context toolbar of a {@link SceneView}. The actions should be
   * specific to a {@link SceneView}. The method returns an empty list if no toolbar is needed.
   */
  @NotNull
  public List<AnAction> getSceneViewContextToolbarActions() {
    return Collections.emptyList();
  }

  /**
   * Returns an action with the status icon for a {@link SceneView}.
   * The method returns null when the status icon is not needed.
   */
  @Nullable
  public AnAction getSceneViewStatusIconAction() {
    return null;
  }

  /**
   * Creates a {@link LabelPanel} with a label for a {@link SceneView}.
   */
  @NotNull
  public LabelPanel createSceneViewLabel(@NotNull SceneView sceneView, CoroutineScope scope) {
    return new LabelPanel(sceneView.getSceneManager().getModel().getModelDisplayName(),
                          sceneView.getSceneManager().getModel().getTooltip(),
                          scope);
  }

  /**
   * Creates a {@link SceneViewErrorsPanel} for a {link SceneView}.
   */
  @NotNull
  public JComponent createErrorPanel(@NotNull SceneView sceneView) {
    return new SceneViewErrorsPanel(() -> {
      // If the flag COMPOSE_PREVIEW_KEEP_IMAGE_ON_ERROR is enabled and  there is a valid image, never display the error panel.
      if (LayoutlibSceneManagerUtilsKt.hasValidImage(sceneView) && StudioFlags.PREVIEW_KEEP_IMAGE_ON_ERROR.get())
        return SceneViewErrorsPanel.Style.HIDDEN;
      if (LayoutlibSceneManagerUtilsKt.hasRenderErrors(sceneView)) return SceneViewErrorsPanel.Style.SOLID;
      return SceneViewErrorsPanel.Style.HIDDEN;
    });
  }

  /**
   * Returns the left bar for a {@link SceneView}.
   * It is at the left of the {@link SceneView} and it contains overlay actions
   * provided in {@link com.android.tools.idea.ui.designer.overlays.OverlayMenuAction}.
   * It only gets displayed if a plugin implementing
   * {@link com.android.tools.idea.ui.designer.overlays.OverlayProvider}is installed
   * and an overlay is being displayed/ cached.
   */
  @Nullable
  public JComponent getSceneViewLeftBar(@NotNull SceneView sceneView) {
    return null;
  }

  /**
   * Returns the right bar for a {@link SceneView}.
   * It is at the right of the {@link SceneView}.
   */
  @Nullable
  public JComponent getSceneViewRightBar(@NotNull SceneView sceneView) {
    return null;
  }
}
