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

import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

public interface OverlayProvider {
  /**
   * Returns the display name of the plugin
   */
  @NotNull
  String getPluginName();

  /**
   * Returns the icon of the plugin
   */
  @Nullable
  Icon getPluginIcon();

  /**
   * When called, this triggers the process of adding a new overlay, and returns a {@link Promise} which will be completed
   * when the overlay is chosen.
   */
  @NotNull
  Promise<OverlayData> addOverlay();

  /**
   * When called, this triggers the process of fetching an overlay, and returns a {@link Promise} which will be completed
   * when the overlay is fetched.
   * @param overlayId the ID of the overlay to be fetched
   */
  @NotNull
  Promise<OverlayData> getOverlay(@NotNull String overlayId);
}
