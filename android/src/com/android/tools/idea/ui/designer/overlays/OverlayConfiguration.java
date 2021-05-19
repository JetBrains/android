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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.extensions.ExtensionPointName;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A class containing the overlay configuration as well as helper methods for handling the overlays
 */
public final class OverlayConfiguration {
  public static final ExtensionPointName<OverlayProvider>
    EP_NAME = ExtensionPointName.create("com.android.tools.idea.ui.designer.overlays.overlayProvider");
  private static final OverlayData PLACEHOLDER_OVERLAY =
    new OverlayData(new OverlayEntry("", null), "", null);
  //TODO: make this customisable
  private float myOverlayAlpha = 0.5f;
  private final List<OverlayData> myOverlays = new ArrayList<>();
  private BufferedImage myOverlayImage = null;
  private OverlayEntry myCurrentOverlayEntry;
  private boolean myOverlayVisible = false;

  /**
   * Loads any persisted overlays along with the placeholder overlay
   */
  public OverlayConfiguration() {
    loadOverlayList();
  }

  /**
   * Returns whether an overlay is being displayed
   */
  public boolean getOverlayVisibility() {
    return myOverlayVisible;
  }

  /**
   * Returns the list of existing overlays
   */
  public List<OverlayData> getAllOverlays() {
    return  ImmutableList.copyOf(myOverlays);
  }

  /**
   * Returns the current overlay image
   */
  public BufferedImage getOverlayImage() {
    return myOverlayImage;
  }

  /**
   * Returns the current {@link OverlayEntry}
   */
  public OverlayEntry getCurrentOverlayEntry() {
    return myCurrentOverlayEntry;
  }

  /**
   * Returns the overlay alpha channel value
   */
  public float getOverlayAlpha() {
    return myOverlayAlpha;
  }

  /**
   * Updates the current {@link OverlayEntry}, and the overlay image)
   * It also updates the display name of the overlay.
   * @param overlayData
   */
  public void updateOverlay(OverlayData overlayData) {
    myOverlayImage = overlayData.getOverlayImage();
    myCurrentOverlayEntry = overlayData.getOverlayEntry();
    myOverlayVisible = true;

    if(!overlayData.equals(PLACEHOLDER_OVERLAY)) {
      int index = myOverlays.indexOf(overlayData);
      if(index != -1) {
        myOverlays.get(index).setOverlayName(overlayData.getOverlayName());
        OverlayPersistentStateService overlayPersistentStateService
          = OverlayPersistentStateService.getInstance();
        overlayPersistentStateService
          .updateOverlayName(myCurrentOverlayEntry, overlayData.getOverlayName());
      } else {
        Notifications.Bus.notify(
          new Notification("Manage Overlays",
                           null,
                           "Internal error",
                           null,
                           "Overlay does not exist.",
                           NotificationType.ERROR,
                           null));
      }
    }
  }

  /**
   * Clears the current overlay
   */
  public void clearCurrentOverlay() {
    myCurrentOverlayEntry = null;
    myOverlayImage = null;
    myOverlayVisible = false;
  }

  /**
   * Adds a new overlay as {@link OverlayData} to the list of existing overlays,
   * and persists this data.
   *
   * @param overlay the overlay data to be added
   */
  public void addOverlay(OverlayData overlay) {
    if (!myOverlays.contains(overlay)) {
      myOverlays.add(overlay);

      OverlayPersistentStateService overlayPersistentStateService = OverlayPersistentStateService.getInstance();
      overlayPersistentStateService.addOverlayData(overlay.getOverlayEntry(), overlay.getOverlayName());
    }

    updateOverlay(overlay);
  }

  /**
   * Returns whether the the placeholder overlay is being displayed.
   */
  public boolean isPlaceholderVisible() {
    return Objects.equals(myCurrentOverlayEntry, PLACEHOLDER_OVERLAY.getOverlayEntry());
  }

  /**
   * Displays the placeholder overlay
   */
  public void showPlaceholder() {
    updateOverlay(PLACEHOLDER_OVERLAY);
  }

  /**
   * Clears the placeholder overlay
   */
  public void hidePlaceholder() {
    if (isPlaceholderVisible()) {
      clearCurrentOverlay();
    }
  }

  /**
   * Displays the currently cached overlay
   */
  public void showCachedOverlay() {
    myOverlayVisible = true;
  }

  /**
   * Hides the currently cached overlay
   */
  public void hideCachedOverlay() {
    myOverlayVisible = false;
  }

  /**
   *  Returns whether an overlay is displayed/ cache
   */
  public boolean isOverlayPresent() {
    return myOverlayImage != null || isPlaceholderVisible();
  }

  /**
   * Deletes a list of overlays from the list of existing overlays.
   *
   * @param overlays the list of overlays to be deleted
   */
  public void removeOverlays(List<OverlayData> overlays) {
    for (OverlayData overlay : overlays) {
      removeOverlayFromList(overlay.getOverlayEntry());
    }
  }

  /**
   * Removes a single overlay from the list of existing overlays, and it also removes it
   * from the persisted data.
   *
   * @param overlayEntry the {@link OverlayEntry} of the overlay to be removed
   */
  public void removeOverlayFromList(OverlayEntry overlayEntry) {
    myOverlays.removeIf(overlayData -> overlayData.getOverlayEntry().equals(overlayEntry));

    if (Objects.equals(myCurrentOverlayEntry, overlayEntry)) {
      clearCurrentOverlay();
    }

    OverlayPersistentStateService overlayPersistentStateService = OverlayPersistentStateService.getInstance();
    overlayPersistentStateService.deleteOverlayData(overlayEntry);
  }

  /**
   * Loads the existing overlays from persisted overlay data.
   */
  public void loadOverlayList() {
    OverlayPersistentStateService overlayPersistentStateService = OverlayPersistentStateService.getInstance();
    List<OverlayProvider> providers = EP_NAME.getExtensionsIfPointIsRegistered();

    if (!providers.isEmpty() && overlayPersistentStateService != null) {
      for (int i = 0; i < overlayPersistentStateService.getSize(); i++) {
        String persistedProvider = overlayPersistentStateService.providers.get(i);
        OverlayProvider provider =  Iterables.find(providers, p -> p.getClass().getSimpleName().equals(persistedProvider), null);
        if(provider != null) {
          OverlayEntry overlayEntry = new OverlayEntry(overlayPersistentStateService.overlayIds.get(i), provider);
          OverlayData data = new OverlayData(overlayEntry, overlayPersistentStateService.overlayNames.get(i), null);
          myOverlays.add(data);
        }
      }
    }
  }
}
