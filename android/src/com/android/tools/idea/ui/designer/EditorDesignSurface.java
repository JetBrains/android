/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.ui.designer;

import com.android.tools.adtui.common.AdtPrimaryPanel;
import com.android.tools.configurations.Configuration;
import com.android.tools.idea.ui.designer.overlays.OverlayConfiguration;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.Iterables;
import java.awt.LayoutManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A layout editor design surface.
 */
public abstract class EditorDesignSurface extends AdtPrimaryPanel {

  private final OverlayConfiguration myOverlayConfiguration = new OverlayConfiguration();

  public EditorDesignSurface(LayoutManager layout) {
    super(layout);
  }

  /**
   * @deprecated use {@link #getConfigurations()} instead. Using this method means that you won't support multi-model configurations
   */
  @Deprecated
  @Nullable
  public Configuration getConfiguration() {
    return Iterables.getFirst(getConfigurations(), null);
  }

  /**
   * Returns all the configurations represented in the surface. Since there are multiple models, there can be multiple configurations
   * being rendered.
   */
  @NotNull
  abstract public ImmutableCollection<Configuration> getConfigurations();

   /**
   * Returns all the configurations represented in the surface returned by getConfigurations() as a standard Java list.
   */
  @NotNull
  public List<Configuration> getConfigurationsAsList() {
    return new ArrayList(getConfigurations());
  }

  /**
   * When called, this will trigger a re-inflate and refresh of the layout and returns a {@link CompletableFuture} that will complete
   * when the refresh has completed.
   * Only call this method if the action is initiated by the user, call {@link #forceRefresh()} otherwise.
   */
  @NotNull
  abstract public CompletableFuture<Void> forceUserRequestedRefresh();

  /**
   * When called, this will trigger a re-inflate and refresh of the layout and returns a {@link CompletableFuture} that will complete
   * when the refresh has completed.
   */
  @NotNull
  abstract public CompletableFuture<Void> forceRefresh();

  /**
   * Returns the {@link OverlayConfiguration} of the {@link EditorDesignSurface}
   */
  @NotNull
  public OverlayConfiguration getOverlayConfiguration() {
    return myOverlayConfiguration;
  }
}
