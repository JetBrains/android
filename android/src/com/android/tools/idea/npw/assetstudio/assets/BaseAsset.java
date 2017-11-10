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
package com.android.tools.idea.npw.assetstudio.assets;

import com.android.tools.idea.npw.assetstudio.wizard.PersistentState;
import com.android.tools.idea.observable.AbstractProperty;
import com.android.tools.idea.observable.core.*;
import com.android.tools.idea.observable.expressions.bool.BooleanExpression;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.components.PersistentStateComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Base class for all asset types which can be converted into Android icons. See also
 * {@link com.android.tools.idea.npw.assetstudio.IconGenerator}, which handles the conversion.
 *
 * Asset fields are all {@link AbstractProperty} instances, which allows for assets to be easily
 * bound to and modified by UI widgets.
 */
public abstract class BaseAsset implements PersistentStateComponent<PersistentState> {
  @SuppressWarnings("UseJBColor") // Intentionally not using JBColor for Android icons.
  private static final Color DEFAULT_COLOR = Color.BLACK;

  private static final String TRIMMED_PROPERTY = "trimmed";
  private static final String PADDING_PERCENT_PROPERTY = "paddingPercent";
  private static final String SCALING_PERCENT_PROPERTY = "scalingPercent";
  private static final String COLOR_PROPERTY = "color";

  private final BoolProperty myTrimmed = new BoolValueProperty();
  private final IntProperty myPaddingPercent = new IntValueProperty(0);
  private final IntProperty myScalingPercent = new IntValueProperty(100);
  private final ObjectProperty<Color> myColor = new ObjectValueProperty<>(DEFAULT_COLOR);

  /**
   * Whether or not transparent space should be removed from the asset before rendering.
   */
  @NotNull
  public BoolProperty trimmed() {
    return myTrimmed;
  }

  /**
   * A percentage of padding (transparent space) to add around the asset before rendering.
   *
   * Expected values are between -10 (zoomed in enough to clip some of the asset's edges) and 50
   * (zoomed out so that the image is half size and centered).
   */
  @NotNull
  public IntProperty paddingPercent() {
    return myPaddingPercent;
  }

  @NotNull
  public IntProperty scalingPercent() {
    return myScalingPercent;
  }

  /**
   * A color to use when rendering this image. Not all asset types are affected by this color.
   */
  @NotNull
  public ObjectProperty<Color> color() {
    return myColor;
  }

  /**
   * Returns an observable boolean reflecting whether the asset is resizable or not.
   */
  @NotNull
  public ObservableBool isResizable() {
    return BooleanExpression.ALWAYS_TRUE;
  }

  /**
   * Returns the image represented by this asset, or null if the asset is not in a valid state for generating
   * the image. In the latter case the method may also return a non-zero {@link ListenableFuture} wrapping
   * a null value.
   */
  @Nullable
  public abstract ListenableFuture<BufferedImage> toImage();

  @Override
  @NotNull
  public PersistentState getState() {
    PersistentState state = new PersistentState();
    state.set(TRIMMED_PROPERTY, myTrimmed.get(), false);
    state.set(PADDING_PERCENT_PROPERTY, myPaddingPercent.get(), 0);
    state.set(SCALING_PERCENT_PROPERTY, myScalingPercent.get(), 100);
    state.set(COLOR_PROPERTY, myColor.get(), DEFAULT_COLOR);
    return state;
  }

  @Override
  public void loadState(@NotNull PersistentState state) {
    myTrimmed.set(state.get(TRIMMED_PROPERTY, false));
    myPaddingPercent.set(state.get(PADDING_PERCENT_PROPERTY, 0));
    myScalingPercent.set(state.get(SCALING_PERCENT_PROPERTY, 100));
    myColor.set(state.get(COLOR_PROPERTY, DEFAULT_COLOR));
  }
}
