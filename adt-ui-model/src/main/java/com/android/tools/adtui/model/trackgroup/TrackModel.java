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
import org.jetbrains.annotations.NotNull;

/**
 * Data model for Track, a generic UI component for representing horizontal data visualization.
 *
 * @param <M> data model type
 * @param <R> renderer enum type
 */
public class TrackModel<M, R extends Enum> implements DragAndDropModelListElement {
  @NotNull private final M myDataModel;
  @NotNull private final R myRendererType;
  private int myId = -1;
  private String myTitle;

  /**
   * @param dataModel    data model to visualize
   * @param rendererType determines how the data model is visualized
   * @param title        string to be displayed in the header
   */
  public TrackModel(@NotNull M dataModel, @NotNull R rendererType, String title) {
    myDataModel = dataModel;
    myRendererType = rendererType;
    myTitle = title;
  }

  @NotNull
  public M getDataModel() {
    return myDataModel;
  }

  @NotNull
  public R getRendererType() {
    return myRendererType;
  }

  public String getTitle() {
    return myTitle;
  }

  public void setTitle(String title) {
    myTitle = title;
  }

  /**
   * @return a unique ID needed for being in a drag and drop list. -1 if it's not added to a {@link TrackGroupModel} yet.
   */
  @Override
  public int getId() {
    return myId;
  }

  /**
   * Used by track model container (e.g. {@link TrackGroupModel} to set unique IDs automatically.
   */
  protected TrackModel setId(int id) {
    myId = id;
    return this;
  }
}
