/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.adtui.model.trackgroup;

import com.android.tools.adtui.model.DragAndDropModelListElement;
import com.android.tools.adtui.model.TooltipModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Data model for Track, a generic UI component for representing horizontal data visualization.
 *
 * @param <M> data model type
 * @param <R> renderer enum type
 */
public class TrackModel<M, R extends Enum> implements DragAndDropModelListElement {
  private final M myDataModel;
  private final R myRendererType;
  private final String myTitle;
  private final boolean myHideHeader;
  private final int myId;

  /**
   * A track may have display different tooltips for its child components, so we need to provide a way to change the currently active
   * tooltip model.
   */
  @Nullable private TooltipModel myActiveTooltipModel;

  private TrackModel(@NotNull Builder<M, R> builder) {
    myDataModel = builder.myDataModel;
    myRendererType = builder.myRendererType;
    myTitle = builder.myTitle;
    myHideHeader = builder.myHideHeader;
    myId = builder.myId;
    myActiveTooltipModel = builder.myDefaultTooltipModel;
  }

  @NotNull
  public M getDataModel() {
    return myDataModel;
  }

  @NotNull
  public R getRendererType() {
    return myRendererType;
  }

  @NotNull
  public String getTitle() {
    return myTitle;
  }

  /**
   * @return whether to hide the track's header column.
   */
  public boolean getHideHeader() {
    return myHideHeader;
  }

  /**
   * @return a unique ID needed for being in a drag and drop list. -1 if it's not added to a {@link TrackGroupModel} yet.
   */
  @Override
  public int getId() {
    return myId;
  }

  @Nullable
  public TooltipModel getActiveTooltipModel() {
    return myActiveTooltipModel;
  }

  public void setActiveTooltipModel(@Nullable TooltipModel tooltipModel) {
    myActiveTooltipModel = tooltipModel;
  }

  /**
   * Instantiates a new builder with parameters required for the track model.
   *
   * @param dataModel    data model to visualize
   * @param rendererType determines how the data model is visualized
   * @param title        string to be displayed in the header
   */
  public static <M, R extends Enum> Builder<M, R> newBuilder(@NotNull M dataModel, @NotNull R rendererType, @NotNull String title) {
    return new Builder<>(dataModel, rendererType, title);
  }

  public static class Builder<M, R extends Enum> {
    private final M myDataModel;
    private final R myRendererType;
    private final String myTitle;
    private boolean myHideHeader;
    private int myId;
    private TooltipModel myDefaultTooltipModel;

    private Builder(@NotNull M dataModel, @NotNull R rendererType, @NotNull String title) {
      myDataModel = dataModel;
      myRendererType = rendererType;
      myTitle = title;
      myHideHeader = false;
      myId = -1;
      myDefaultTooltipModel = null;
    }

    public Builder<M, R> setHideHeader(boolean hideHeader) {
      myHideHeader = hideHeader;
      return this;
    }

    /**
     * Only exposed to track model container (e.g. {@link TrackGroupModel} to set unique IDs automatically.
     */
    protected Builder<M, R> setId(int id) {
      myId = id;
      return this;
    }

    /**
     * Sets the default tooltip model for the track when it's instantiated.
     */
    public Builder<M, R> setDefaultTooltipModel(TooltipModel defaultTooltipModel) {
      myDefaultTooltipModel = defaultTooltipModel;
      return this;
    }

    public TrackModel<M, R> build() {
      return new TrackModel<>(this);
    }
  }
}
