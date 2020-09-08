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
import static icons.StudioIcons.LayoutInspector.CLEAR_OVERLAY;
import static icons.StudioIcons.LayoutInspector.LOAD_OVERLAY;
import static icons.StudioIcons.LayoutInspector.MODE_3D;

import com.android.tools.adtui.actions.DropDownAction;
import com.android.tools.idea.ui.designer.EditorDesignSurface;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Toggleable;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;

public class OverlayMenuAction extends DropDownAction {
  private final EditorDesignSurface mySurface;

  /**
   * Create a menu for managing overlays.
   *
   * @param surface The current {@link EditorDesignSurface} where this action is displayed
   */
  public OverlayMenuAction(@NotNull EditorDesignSurface surface) {
    //TODO add icon
    super("Overlays Menu", "Overlays Menu", MODE_3D);
    mySurface = surface;
  }

  @Override
  protected boolean updateActions(@NotNull DataContext context) {
    List<OverlayProvider> providers =
      OverlayConfiguration.EP_NAME.getExtensionsIfPointIsRegistered();

    removeAll();
    if (!providers.isEmpty()) {
      DefaultActionGroup overlayGroup = DefaultActionGroup.createPopupGroup(() -> "Overlays");

      OverlayEntry currentOverlay = mySurface.getOverlayConfiguration().getCurrentOverlayEntry();
      for (OverlayData overlay : mySurface.getOverlayConfiguration().getAllOverlays()) {
        boolean isCurrentlySelected = false;
        if (overlay.getOverlayEntry().equals(currentOverlay)) {
          isCurrentlySelected = true;
        }
        overlayGroup.add(new ToggleOverlayAction(mySurface,
                                                 overlay.getOverlayName(),
                                                 overlay.getOverlayEntry(),
                                                 isCurrentlySelected));
      }

      add(overlayGroup);

      for (OverlayProvider provider : providers) {
        add(new AddOverlayAction(mySurface, provider));
      }

      add(new DeleteOverlayAction(mySurface));

      addSeparator();

      add(new UpdateOverlayAction(mySurface));
      add(new ToggleCachedOverlayAction(mySurface));
      add(new CancelOverlayAction(mySurface));
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
    private final EditorDesignSurface mySurface;

    ToggleOverlayAction(@NotNull EditorDesignSurface surface,
                        @NotNull String title,
                        @NotNull OverlayEntry overlayEntry,
                        boolean checked) {
      super(title, null, overlayEntry.getOverlayProvider().getPluginIcon());
      myOverlayEntry = overlayEntry;
      mySurface = surface;
      getTemplatePresentation().putClientProperty(SELECTED_PROPERTY, checked);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      OverlayConfiguration overlayConfiguration = mySurface.getOverlayConfiguration();
      OverlayEntry currentOverlay = overlayConfiguration.getCurrentOverlayEntry();
      if (myOverlayEntry.equals(currentOverlay)) {
        overlayConfiguration.clearCurrentOverlay();
      }
      else {
        overlayConfiguration.showPlaceholder();
        Promise<OverlayData> promise =
          myOverlayEntry.getOverlayProvider().getOverlay(myOverlayEntry.getId());
        promise.onSuccess(result -> {
          if (mySurface.getOverlayConfiguration().isPlaceholderVisible()) {
            result.setOverlayProvider(myOverlayEntry.getOverlayProvider());
            overlayConfiguration.updateOverlay(result);
          }
        });
        promise.onError(t -> {
          if (mySurface.getOverlayConfiguration().isPlaceholderVisible()) {
            overlayConfiguration.hidePlaceholder();

            if (t instanceof OverlayNotFoundException) {
              overlayConfiguration.removeOverlayFromList(myOverlayEntry);
              Notifications.Bus.notify(
                new Notification("Manage Overlays",
                                 null,
                                 "Error fetching overlay",
                                 null,
                                 "The overlay you requested does not exist anymore.",
                                 NotificationType.ERROR,
                                 null));
            }
            else {
              Notifications.Bus.notify(
                new Notification("Manage Overlays",
                                 null,
                                 "Error fetching overlay",
                                 null,
                                 "There was an error fetching the overlay. Please try again.",
                                 NotificationType.ERROR,
                                 null));
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
    EditorDesignSurface mySurface;

    public ToggleCachedOverlayAction(@NotNull EditorDesignSurface surface) {
      super("Toggle Overlay", "Toggle current overlay on/off", LAYER);
      mySurface = surface;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setVisible(mySurface.getOverlayConfiguration().isOverlayPresent());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      OverlayConfiguration configuration = mySurface.getOverlayConfiguration();
      if (configuration.getOverlayImage() != null && !configuration.isPlaceholderVisible()) {
        if (!configuration.getOverlayVisibility()) {
          configuration.showCachedOverlay();
        }
        else {
          configuration.hideCachedOverlay();
        }
        mySurface.repaint();
      }
      else {
        Notifications.Bus.notify(
          new Notification("Manage Overlays",
                           null,
                           "Error toggling overlay",
                           null,
                           "There was no overlay to be toggled. Please select an overlay first.",
                           NotificationType.WARNING,
                           null));
      }
    }
  }

  public static class AddOverlayAction extends AnAction {
    @NotNull
    private final OverlayProvider myOverlayProvider;
    @NotNull
    private final EditorDesignSurface mySurface;

    AddOverlayAction(@NotNull EditorDesignSurface surface,
                     @NotNull OverlayProvider provider) {
      super("Add " + provider.getPluginName() + " Overlay...", null, LOAD_OVERLAY);
      myOverlayProvider = provider;
      mySurface = surface;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      OverlayConfiguration overlayConfiguration = mySurface.getOverlayConfiguration();
      overlayConfiguration.showPlaceholder();
      Promise<OverlayData> promise = myOverlayProvider.addOverlay();
      promise.onSuccess(result -> {
        if (overlayConfiguration.isPlaceholderVisible()) {
          result.setOverlayProvider(myOverlayProvider);
          overlayConfiguration.addOverlay(result);
        }
      });
      promise.onError(t -> {
        if (overlayConfiguration.isPlaceholderVisible()) {
          overlayConfiguration.hidePlaceholder();
          Notifications.Bus.notify(
            new Notification("Manage Overlays",
                             null,
                             "Error fetching overlay",
                             null,
                             "There was an error fetching the overlay. Please try again.",
                             NotificationType.ERROR,
                             null));
        }
      });
    }
  }

  public static class UpdateOverlayAction extends AnAction {
    @NotNull
    private final EditorDesignSurface mySurface;

    public UpdateOverlayAction(@NotNull EditorDesignSurface surface) {
      super("Reload Overlay", "Reload the current overlay", LOOP);
      mySurface = surface;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setVisible(mySurface.getOverlayConfiguration().isOverlayPresent());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      OverlayConfiguration overlayConfiguration = mySurface.getOverlayConfiguration();
      OverlayEntry currentOverlay = overlayConfiguration.getCurrentOverlayEntry();
      if (overlayConfiguration.getOverlayImage() != null
          && !overlayConfiguration.isPlaceholderVisible()) {
        overlayConfiguration.showPlaceholder();
        Promise<OverlayData> promise =
          currentOverlay.getOverlayProvider().getOverlay(currentOverlay.getId());
        promise.onSuccess(result -> {
          if (mySurface.getOverlayConfiguration().isPlaceholderVisible()) {
            result.setOverlayProvider(currentOverlay.getOverlayProvider());
            overlayConfiguration.updateOverlay(result);
          }
        });
        promise.onError(t -> {
          if (mySurface.getOverlayConfiguration().isPlaceholderVisible()) {
            overlayConfiguration.hidePlaceholder();

            if (t instanceof OverlayNotFoundException) {
              overlayConfiguration.removeOverlayFromList(currentOverlay);
              Notifications.Bus.notify(
                new Notification("Manage Overlays",
                                 null,
                                 "Error fetching overlay",
                                 null,
                                 "The overlay you requested does not exist anymore.",
                                 NotificationType.ERROR,
                                 null));
            }
            else {
              Notifications.Bus.notify(
                new Notification("Manage Overlays",
                                 null,
                                 "Error fetching overlay",
                                 null,
                                 "There was an error fetching the overlay. Please try again.",
                                 NotificationType.ERROR,
                                 null));
            }
          }
        });
      }
      else {
        Notifications.Bus.notify(
          new Notification("Manage Overlays",
                           null,
                           "Update error",
                           null,
                           "There is no overlay to be updated. Please select an overlay before trying to update it.",
                           NotificationType.WARNING,
                           null));
      }
    }
  }

  @VisibleForTesting
  static class DeleteOverlayAction extends AnAction {
    @NotNull
    private final EditorDesignSurface mySurface;

    DeleteOverlayAction(@NotNull EditorDesignSurface surface) {
      super("Delete Overlay...", null, DELETE);
      mySurface = surface;
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation()
        .setVisible(!mySurface.getOverlayConfiguration().getAllOverlays().isEmpty());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      List<OverlayData> overlays = mySurface.getOverlayConfiguration().getAllOverlays();
      //TODO:  Not sure what dialog is normally used for picking objects/files
      ChooseOverlayDialog chooser =
        new ChooseOverlayDialog(overlays,
                                "Choose overlay",
                                "Choose the overlay you want to delete");
      chooser.show();

      List<OverlayData> chosen = chooser.getChosenElements();
      mySurface.getOverlayConfiguration().removeOverlays(chosen);
    }
  }

  /**
   * Class for clearing an overlay/ cancelling an overlay method
   */
  public static class CancelOverlayAction extends AnAction {
    private final EditorDesignSurface mySurface;

    /**
     * Creates the action and sets the boolean isClearOverlayActionAdded to true.
     * This is done so that this action is added only once per {@link EditorDesignSurface}
     *
     * @param surface - the design surface of the action
     */
    public CancelOverlayAction(@NotNull EditorDesignSurface surface) {
      super("Cancel Overlay", "Disable current overlay", CLEAR_OVERLAY);
      mySurface = surface;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setVisible(mySurface.getOverlayConfiguration().isOverlayPresent());
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
      if (mySurface.getOverlayConfiguration().isOverlayPresent()) {
        mySurface.getOverlayConfiguration().clearCurrentOverlay();
        mySurface.repaint();
      }
      else {
        Notifications.Bus.notify(
          new Notification("Manage Overlays",
                           null,
                           "Error cancelling overlay action",
                           null,
                           "There is no overlay action to be cancelled.",
                           NotificationType.WARNING,
                           null));
      }
    }
  }
}
