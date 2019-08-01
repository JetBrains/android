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
import com.android.tools.adtui.model.DragAndDropModelListElement;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;

/**
 * Data model for TrackGroup, a collapsible UI component that contains a list of Tracks.
 */
public class TrackGroupModel extends DragAndDropListModel<TrackModel> implements DragAndDropModelListElement {
  /**
   * Use this to generate unique (within this group) IDs for {@link TrackModel}s in this group.
   */
  private static final AtomicInteger TRACK_ID_GENERATOR = new AtomicInteger();

  private int myId = -1;
  private String myTitle;

  /**
   * @param title string to be displayed in the header
   */
  public TrackGroupModel(String title) {
    myTitle = title;
  }

  /**
   * Add a {@link TrackModel} to the group.
   *
   * @param trackModel {@link TrackModel} to add
   * @param <M>        data model type
   * @param <R>        renderer enum type
   */
  public <M, R extends Enum> void addTrackModel(@NotNull TrackModel<M, R> trackModel) {
    // add() is disabled in DragAndDropListModel to support dynamically reordering elements. Use insertOrderedElement() instead.
    insertOrderedElement(trackModel.setId(TRACK_ID_GENERATOR.getAndIncrement()));
  }

  public String getTitle() {
    return myTitle;
  }

  public void setTitle(String title) {
    myTitle = title;
  }

  /**
   * @return a unique ID needed for being in a drag and drop list. -1 if it's not in a {@link TrackGroupListModel} yet.
   */
  @Override
  public int getId() {
    return myId;
  }

  /**
   * Used by container (e.g. {@link TrackGroupListModel}) to set unique IDs automatically.
   */
  public TrackGroupModel setId(int id) {
    myId = id;
    return this;
  }
}
