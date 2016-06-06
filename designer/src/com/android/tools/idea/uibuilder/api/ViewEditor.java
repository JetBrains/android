/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.api;

import com.android.resources.ResourceType;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.uibuilder.model.AndroidCoordinate;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static com.android.SdkConstants.VALUE_N_DP;
import static com.android.resources.Density.DEFAULT_DENSITY;

/**
 * The UI builder / layout editor as exposed to {@link ViewHandler} instances.
 * This allows the view handlers to query the surrounding editor for more information.
 */
public abstract class ViewEditor {
  public abstract int getDpi();

  /**
   * Converts a device independent pixel to a screen pixel for the current screen density
   *
   * @param dp the device independent pixel dimension
   * @return the corresponding pixel dimension
   */
  public int dpToPx(int dp) {
    int dpi = getDpi();
    return dp * dpi / DEFAULT_DENSITY;
  }

  /**
   * Converts a pixel to a dp (device independent pixel) for the current screen density
   *
   * @param px the pixel dimension (in Android screen pixels)
   * @return the corresponding dp dimension
   */
  public int pxToDp(@AndroidCoordinate int px) {
    int dpi = getDpi();
    return px * DEFAULT_DENSITY / dpi;
  }

  /**
   * Converts a pixel to a dp (device independent pixel) for the current screen density,
   * and returns it as a dimension string.
   *
   * @param px the pixel dimension
   * @return the corresponding dp dimension string
   */
  @NotNull
  public String pxToDpWithUnits(int px) {
    return String.format(Locale.US, VALUE_N_DP, pxToDp(px));
  }

  /**
   * Returns the version used to compile the module containing this editor with
   */
  @Nullable
  public abstract AndroidVersion getCompileSdkVersion();

  /**
   * Returns the minSdkVersion for the module containing this editor
   */
  @NotNull
  public abstract AndroidVersion getMinSdkVersion();

  /**
   * Returns the targetSdkVersion for the module containing this editor
   */
  @NotNull
  public abstract AndroidVersion getTargetSdkVersion();

  /**
   * Returns the configuration for the editor
   */
  @NotNull
  public abstract Configuration getConfiguration();

  /**
   * Returns the model for the editor
   */
  @NotNull
  public abstract NlModel getModel();

  /**
   * Returns true if the current module depends on the specified library.
   *
   * @param artifact library artifact e.g. "com.android.support:appcompat-v7"
   */
  public abstract boolean isModuleDependency(@NotNull String artifact);

  /**
   * Measures the children of the given parent and returns them as a map to view info instances.
   *
   * @param parent the parent component whose children we want to measure
   * @param filter an optional filter we'll apply to the attributes of each of the children
   * @return a map from child to bounds information, if possible
   */
  @Nullable
  public abstract Map<NlComponent, Dimension> measureChildren(@NotNull NlComponent parent, @Nullable RenderTask.AttributeFilter filter);

  @Nullable
  public final String displayResourceInput(@NotNull Collection<ResourceType> types) {
    return displayResourceInput("", types);
  }

  @Nullable
  public abstract String displayResourceInput(@NotNull String title, @NotNull Collection<ResourceType> types);

  @Nullable
  public abstract String displayClassInput(@NotNull Set<String> superTypes,
                                           @Nullable Predicate<String> filter,
                                           @Nullable String currentValue);
}
