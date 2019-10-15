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
import org.jetbrains.annotations.NotNull;

/**
 * Data model for TrackGroup, a collapsible UI component that contains a list of Tracks.
 */
public class TrackGroupModel extends DragAndDropListModel<TrackModel> {
  /**
   * Use this to generate unique (within this group) IDs for {@link TrackModel}s in this group.
   */
  private static final AtomicInteger TRACK_ID_GENERATOR = new AtomicInteger();

  private final String myTitle;
  private final boolean myCollapsedInitially;
  private final boolean myHideHeader;
  private final int myTrackLimit;

  /**
   * Use builder to instantiate this class.
   */
  private TrackGroupModel(Builder builder) {
    myTitle = builder.myTitle;
    myCollapsedInitially = builder.myCollapsedInitially;
    myHideHeader = builder.myHideHeader;
    myTrackLimit = builder.myTrackLimit;
  }

  /**
   * Add a {@link TrackModel} to the group.
   *
   * @param builder    to build the {@link TrackModel} to add
   * @param <M>        data model type
   * @param <R>        renderer enum type
   */
  public <M, R extends Enum> void addTrackModel(@NotNull TrackModel.Builder<M, R> builder) {
    // add() is disabled in DragAndDropListModel to support dynamically reordering elements. Use insertOrderedElement() instead.
    insertOrderedElement(builder.setId(TRACK_ID_GENERATOR.getAndIncrement()).build());
  }

  public String getTitle() {
    return myTitle;
  }

  /**
   * @return whether the track group is collapsed initially.
   */
  public boolean isCollapsedInitially() {
    return myCollapsedInitially;
  }

  /**
   * @return whether to hide the group header.
   */
  public boolean getHideHeader() {
    return myHideHeader;
  }

  /**
   * @return the number limit of tracks to display.
   */
  public int getTrackLimit() {
    return myTrackLimit;
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static class Builder {
    private String myTitle;
    private boolean myCollapsedInitially;
    private boolean myHideHeader;
    private int myTrackLimit;

    private Builder() {
      myTitle = "";
      myCollapsedInitially = false;
      myHideHeader = false;
      myTrackLimit = Integer.MAX_VALUE;
    }

    /**
     * @param title string to be displayed in the header
     */
    public Builder setTitle(String title) {
      myTitle = title;
      return this;
    }

    public Builder setCollapsedInitially(boolean collapsedInitially) {
      myCollapsedInitially = collapsedInitially;
      return this;
    }

    public Builder setHideHeader(boolean hideHeader) {
      myHideHeader = hideHeader;
      return this;
    }

    public Builder setTrackLimit(int trackLimit) {
      myTrackLimit = trackLimit;
      return this;
    }

    public TrackGroupModel build() {
      return new TrackGroupModel(this);
    }
  }
}
