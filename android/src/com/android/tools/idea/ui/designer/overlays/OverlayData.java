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

import java.awt.image.BufferedImage;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Class for holding data about an overlay
 */
public class OverlayData {
  private OverlayEntry myOverlayEntry;
  private String myOverlayName;
  private BufferedImage myOverlayImage;

  /**
   * Creates new OverlayData for a given overlay
   * @param overlayEntry the ID of the overlay
   * @param overlayName the display name of the overlay
   * @param overlayImage the overlay image
   */
  public OverlayData(@NotNull OverlayEntry overlayEntry, @NotNull String overlayName, @Nullable BufferedImage overlayImage) {
    myOverlayEntry = overlayEntry;
    myOverlayName = overlayName;
    myOverlayImage = overlayImage;
  }

  /**
   * Returns the {@link OverlayEntry}
   */
  @NotNull
  public OverlayEntry getOverlayEntry() {
    return myOverlayEntry;
  }

  /**
   * Returns the display name of the overlay
   */
  @NotNull
  public String getOverlayName() {
    return myOverlayName;
  }

  /**
   * Returns the overlay image
   */
  public BufferedImage getOverlayImage() {
    return myOverlayImage;
  }

  /**
   * Sets the {@link OverlayEntry}
   * @param overlayEntry the new Overlay
   */
  public void setOverlayEntry(@NotNull OverlayEntry overlayEntry) {
    myOverlayEntry = overlayEntry;
  }

  /**
   * Method to set the overlay provider.
   * @param provider the provider
   */
  public void setOverlayProvider(@NotNull OverlayProvider provider) {
    myOverlayEntry.setOverlayProvider(provider);
  }

  /**
   * Sets the display name of the overlay
   * @param overlayName the new display name
   */
  public void setOverlayName(@NotNull String overlayName) {
    myOverlayName = overlayName;
  }

  /**
   * Sets the overlay image
   * @param overlayImage the new overlay image
   */
  public void setOverlayImage(@Nullable BufferedImage overlayImage) {
    myOverlayImage = overlayImage;
  }

  @Override
  public boolean equals(Object o) {
    return (o instanceof OverlayData) && Objects.equals(myOverlayEntry, ((OverlayData)o).myOverlayEntry);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myOverlayEntry);
  }
}