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

import com.android.tools.adtui.model.DragAndDropListModel;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link TrackGroupModel} list model for drag and drop support.
 */
public class TrackGroupListModel extends DragAndDropListModel<TrackGroupModel> {
  /**
   * Use this to generate unique (within this group) IDs for {@link TrackModel}s in this group.
   */
  private static final AtomicInteger TRACK_GROUP_ID_GENERATOR = new AtomicInteger();

  /**
   * Add a {@link TrackGroupModel} to list model.
   *
   * @param builder for building the model
   * @return the model instantiated from the provided builder
   */
  public TrackGroupModel addTrackGroupModel(TrackGroupModel.Builder builder) {
    // add() is disabled in DragAndDropListModel to support dynamically reordering elements. Use insertOrderedElement() instead.
    TrackGroupModel model = builder.setId(TRACK_GROUP_ID_GENERATOR.getAndIncrement()).build();
    insertOrderedElement(model);
    return model;
  }
}
