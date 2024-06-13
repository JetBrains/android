/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.ui.designer.overlays;

import static icons.StudioIcons.Common.DELETE;
import static icons.StudioIcons.LayoutEditor.Motion.LOOP;
import static icons.StudioIcons.LayoutEditor.Toolbar.LAYER;
import static icons.StudioIcons.LayoutInspector.Toolbar.CLEAR_OVERLAY;
import static icons.StudioIcons.LayoutInspector.Toolbar.LOAD_OVERLAY;
import static icons.StudioIcons.LayoutInspector.Toolbar.MODE_3D;

import com.android.tools.adtui.actions.DropDownAction;
import com.android.tools.idea.ui.designer.EditorDesignSurface;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Toggleable;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;

public class OverlayMenuAction extends DropDownAction {
  private final OverlayConfiguration myOverlayConfiguration;
  private final Runnable myRepaint;

  /**
   * Create a menu for managing overlays.
   *
   * @param overlayConfiguration The current {@link OverlayConfiguration} for this action
   * @param repaint target component
   */
  public OverlayMenuAction(@NotNull OverlayConfiguration overlayConfiguration, @NotNull Runnable repaint) {
    //TODO add icon
    super("Overlays Menu", "Overlays Menu", MODE_3D);
    myOverlayConfiguration = overlayConfiguration;
    myRepaint = repaint;
  }

  @Override
  protected boolean updateActions(@NotNull DataContext context) {
    List<OverlayProvider> providers =
      OverlayConfiguration.EP_NAME.getExtensionsIfPointIsRegistered();

    removeAll();
    if (!providers.isEmpty()) {
      DefaultActionGroup overlayGroup = DefaultActionGroup.createPopupGroup(() -> "Overlays");

      OverlayEntry currentOverlay = myOverlayConfiguration.getCurrentOverlayEntry();
      for (OverlayData overlay : myOverlayConfiguration.getAllOverlays()) {
        boolean isCurrentlySelected = false;
        if (overlay.getOverlayEntry().equals(currentOverlay)) {
          isCurrentlySelected = true;
        }
        overlayGroup.add(new ToggleOverlayAction(myOverlayConfiguration,
                                                 overlay.getOverlayName(),
                                                 overlay.getOverlayEntry(),
                                                 isCurrentlySelected));
      }

      add(overlayGroup);

      for (OverlayProvider provider : providers) {
        add(new AddOverlayAction(myOverlayConfiguration, provider));
      }

      add(new DeleteOverlayAction(myOverlayConfiguration));

      addSeparator();

      add(new UpdateOverlayAction(myOverlayConfiguration));
      add(new ToggleCachedOverlayAction(myOverlayConfiguration, myRepaint));
      add(new CancelOverlayAction(myOverlayConfiguration, myRepaint));
    }

    return true;
  }

  /**
   * Class for toggling the visibility of a specific overlay.
   * The overlay is fetched each time and it is not cached.
   */
  @VisibleForTesting
  static class ToggleOverlayAction extends AnAction implements Toggleable {
    @NotNull
    private final OverlayEntry myOverlayEntry;
    @NotNull
    private final OverlayConfiguration myOverlayConfiguration;

    ToggleOverlayAction(@NotNull OverlayConfiguration overlayConfiguration,
                        @NotNull String title,
                        @NotNull OverlayEntry overlayEntry,
                        boolean checked) {
      super(title, null, overlayEntry.getOverlayProvider().getPluginIcon());
      myOverlayEntry = overlayEntry;
      myOverlayConfiguration = overlayConfiguration;
      getTemplatePresentation().putClientProperty(SELECTED_PROPERTY, checked);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      OverlayEntry currentOverlay = myOverlayConfiguration.getCurrentOverlayEntry();
      if (myOverlayEntry.equals(currentOverlay)) {
        myOverlayConfiguration.clearCurrentOverlay();
      }
      else {
        myOverlayConfiguration.showPlaceholder();
        Promise<OverlayData> promise =
          myOverlayEntry.getOverlayProvider().getOverlay(myOverlayEntry.getId());
        promise.onSuccess(result -> {
          if (myOverlayConfiguration.isPlaceholderVisible()) {
            result.setOverlayProvider(myOverlayEntry.getOverlayProvider());
            myOverlayConfiguration.updateOverlay(result);
          }
        });
        promise.onError(t -> {
          if (myOverlayConfiguration.isPlaceholderVisible()) {
            myOverlayConfiguration.hidePlaceholder();

            if (t instanceof OverlayNotFoundException) {
              myOverlayConfiguration.removeOverlayFromList(myOverlayEntry);
              Notifications.Bus.notify(
                new Notification("Manage Overlays",
                                 "Error fetching overlay",
                                 "The overlay you requested does not exist anymore.",
                                 NotificationType.ERROR));
            }
            else {
              Notifications.Bus.notify(
                new Notification("Manage Overlays",
                                 "Error fetching overlay",
                                 "There was an error fetching the overlay. Please try again.",
                                 NotificationType.ERROR));
            }
          }
        });
      }
    }
  }

  /**
   * Class for toggling the visibility of a cached overlay on/off.
   */
  public static class ToggleCachedOverlayAction extends AnAction {
    OverlayConfiguration myOverlayConfiguration;
    Runnable myRepaint;

    public ToggleCachedOverlayAction(@NotNull OverlayConfiguration overlayConfiguration, @NotNull Runnable repaint) {
      super("Toggle Overlay", "Toggle current overlay on/off", LAYER);
      myOverlayConfiguration = overlayConfiguration;
      myRepaint = repaint;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setVisible(myOverlayConfiguration.isOverlayPresent());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      if (myOverlayConfiguration.getOverlayImage() != null && !myOverlayConfiguration.isPlaceholderVisible()) {
        if (!myOverlayConfiguration.getOverlayVisibility()) {
          myOverlayConfiguration.showCachedOverlay();
        }
        else {
          myOverlayConfiguration.hideCachedOverlay();
        }
        myRepaint.run();
      }
      else {
        Notifications.Bus.notify(
          new Notification("Manage Overlays",
                           "Error toggling overlay",
                           "There was no overlay to be toggled. Please select an overlay first.",
                           NotificationType.WARNING));
      }
    }
  }

  public static class AddOverlayAction extends AnAction {
    @NotNull
    private final OverlayProvider myOverlayProvider;
    @NotNull
    private final OverlayConfiguration myOverlayConfiguration;

    AddOverlayAction(@NotNull OverlayConfiguration overlayConfiguration,
                     @NotNull OverlayProvider provider) {
      super("Add " + provider.getPluginName() + " Overlay...", null, LOAD_OVERLAY);
      myOverlayProvider = provider;
      myOverlayConfiguration = overlayConfiguration;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myOverlayConfiguration.showPlaceholder();
      Promise<OverlayData> promise = myOverlayProvider.addOverlay();
      promise.onSuccess(result -> {
        if (myOverlayConfiguration.isPlaceholderVisible()) {
          result.setOverlayProvider(myOverlayProvider);
          myOverlayConfiguration.addOverlay(result);
        }
      });
      promise.onError(t -> {
        if (myOverlayConfiguration.isPlaceholderVisible()) {
          myOverlayConfiguration.hidePlaceholder();
          Notifications.Bus.notify(
            new Notification("Manage Overlays",
                             "Error fetching overlay",
                             "There was an error fetching the overlay. Please try again.",
                             NotificationType.ERROR));
        }
      });
    }
  }

  public static class UpdateOverlayAction extends AnAction {
    @NotNull
    private final OverlayConfiguration myOverlayConfiguration;

    public UpdateOverlayAction(@NotNull OverlayConfiguration overlayConfiguration) {
      super("Reload Overlay", "Reload the current overlay", LOOP);
      myOverlayConfiguration = overlayConfiguration;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setVisible(myOverlayConfiguration.isOverlayPresent());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      OverlayEntry currentOverlay = myOverlayConfiguration.getCurrentOverlayEntry();
      if (myOverlayConfiguration.getOverlayImage() != null
          && !myOverlayConfiguration.isPlaceholderVisible()) {
        myOverlayConfiguration.showPlaceholder();
        Promise<OverlayData> promise =
          currentOverlay.getOverlayProvider().getOverlay(currentOverlay.getId());
        promise.onSuccess(result -> {
          if (myOverlayConfiguration.isPlaceholderVisible()) {
            result.setOverlayProvider(currentOverlay.getOverlayProvider());
            myOverlayConfiguration.updateOverlay(result);
          }
        });
        promise.onError(t -> {
          if (myOverlayConfiguration.isPlaceholderVisible()) {
            myOverlayConfiguration.hidePlaceholder();

            if (t instanceof OverlayNotFoundException) {
              myOverlayConfiguration.removeOverlayFromList(currentOverlay);
              Notifications.Bus.notify(
                new Notification("Manage Overlays",
                                 "Error fetching overlay",
                                 "The overlay you requested does not exist anymore.",
                                 NotificationType.ERROR));
            }
            else {
              Notifications.Bus.notify(
                new Notification("Manage Overlays",
                                 "Error fetching overlay",
                                 "There was an error fetching the overlay. Please try again.",
                                 NotificationType.ERROR));
            }
          }
        });
      }
      else {
        Notifications.Bus.notify(
          new Notification("Manage Overlays",
                           "Update error",
                           "There is no overlay to be updated. Please select an overlay before trying to update it.",
                           NotificationType.WARNING));
      }
    }
  }

  @VisibleForTesting
  static class DeleteOverlayAction extends AnAction {
    @NotNull
    private final OverlayConfiguration myOverlayConfiguration;

    DeleteOverlayAction(@NotNull OverlayConfiguration overlayConfiguration) {
      super("Delete Overlay...", null, DELETE);
      myOverlayConfiguration = overlayConfiguration;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation()
        .setVisible(!myOverlayConfiguration.getAllOverlays().isEmpty());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      List<OverlayData> overlays = myOverlayConfiguration.getAllOverlays();
      //TODO:  Not sure what dialog is normally used for picking objects/files
      ChooseOverlayDialog chooser =
        new ChooseOverlayDialog(overlays,
                                "Choose overlay",
                                "Choose the overlay you want to delete");
      chooser.show();

      List<OverlayData> chosen = chooser.getChosenElements();
      myOverlayConfiguration.removeOverlays(chosen);
    }
  }

  /**
   * Class for clearing an overlay/ cancelling an overlay method
   */
  public static class CancelOverlayAction extends AnAction {
    private final OverlayConfiguration myOverlayConfiguration;
    private final Runnable myRepaint;


    /**
     * Creates the action and sets the boolean isClearOverlayActionAdded to true.
     * This is done so that this action is added only once per {@link EditorDesignSurface}
     *
     * @param overlayConfiguration - configuration
     */
    public CancelOverlayAction(@NotNull OverlayConfiguration overlayConfiguration, @NotNull Runnable repaint) {
      super("Cancel Overlay", "Disable current overlay", CLEAR_OVERLAY);
      myOverlayConfiguration = overlayConfiguration;
      myRepaint = repaint;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setVisible(myOverlayConfiguration.isOverlayPresent());
    }


    /**
     * Clears the current overlay. If the placeholder was being displayed, it is cleared.
     * This causes the overlay action to be canceled as
     * the result of the {@link org.jetbrains.concurrency.Promise} will be ignored
     *
     * @param e Carries information on the invocation place
     */
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      if (myOverlayConfiguration.isOverlayPresent()) {
        myOverlayConfiguration.clearCurrentOverlay();
        myRepaint.run();
      }
      else {
        Notifications.Bus.notify(
          new Notification("Manage Overlays",
                           "Error cancelling overlay action",
                           "There is no overlay action to be cancelled.",
                           NotificationType.WARNING));
      }
    }
  }
}
