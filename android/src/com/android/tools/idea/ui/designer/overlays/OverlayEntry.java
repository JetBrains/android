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

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Class for storing the internal overlay ID along with the {@link OverlayProvider},
 * making it an unique identifier for a single overlay.
 */
public class OverlayEntry {
  private final String myOverlayId;
  private OverlayProvider myOverlayProvider;

  /**
   * Creates an OverlayId using given ID and provider
   * @param overlayId the overlay ID of the overlay
   * @param overlayProvider the provider or the overlay
   */
  public OverlayEntry(@NotNull String overlayId, @Nullable OverlayProvider overlayProvider) {
    myOverlayId = overlayId;
    myOverlayProvider = overlayProvider;
  }

  /**
   * Returns the ID of the overlay
   */
  @NotNull
  public String getId() {
    return myOverlayId;
  }

  /**
   * Returns the overlay provider
   */
  @Nullable
  public OverlayProvider getOverlayProvider() {
    return myOverlayProvider;
  }


  /**
   * Method to set the overlay provider.
   * @param provider the provider
   */
  public void setOverlayProvider(@Nullable OverlayProvider provider) {
    myOverlayProvider = provider;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof OverlayEntry) {
      OverlayEntry that = (OverlayEntry)o;
      return myOverlayId.equals(that.myOverlayId) &&
             Objects.equals(myOverlayProvider, that.myOverlayProvider);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(myOverlayId, myOverlayProvider);
  }
}
