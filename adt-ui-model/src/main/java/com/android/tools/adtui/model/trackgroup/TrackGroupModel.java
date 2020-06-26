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

import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.DragAndDropListModel;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Data model for TrackGroup, a collapsible UI component that contains a list of Tracks.
 */
public final class TrackGroupModel extends DragAndDropListModel<TrackModel> {
  /**
   * Use this to generate unique (within this group) IDs for {@link TrackModel}s in this group.
   */
  private static final AtomicInteger TRACK_ID_GENERATOR = new AtomicInteger();

  private final String myTitle;
  private final String myTitleInfo;
  private final boolean myCollapsedInitially;
  private final boolean myHideHeader;
  private final boolean myTrackSelectable;

  private final AspectObserver myObserver = new AspectObserver();

  /**
   * Use builder to instantiate this class.
   */
  private TrackGroupModel(Builder builder) {
    myTitle = builder.myTitle;
    myTitleInfo = builder.myTitleInfo;
    myCollapsedInitially = builder.myCollapsedInitially;
    myHideHeader = builder.myHideHeader;
    myTrackSelectable = builder.myTrackSelectable;
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
    TrackModel<M, R> trackModel = builder.setId(TRACK_ID_GENERATOR.getAndIncrement()).build();
    insertOrderedElement(trackModel);

    // Listen to track's collapse state change.
    trackModel.getAspectModel().addDependency(myObserver).onChange(TrackModel.Aspect.COLLAPSE_CHANGE, () -> {
      int index = indexOf(trackModel);
      if (index != -1) {
        fireContentsChanged(this, index, index);
      }
    });
  }

  public String getTitle() {
    return myTitle;
  }

  @Nullable
  public String getTitleInfo() {
    return myTitleInfo;
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
   * @return whether the tracks inside this track group are selectable.
   */
  public boolean isTrackSelectable() {
    return myTrackSelectable;
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static final class Builder {
    private String myTitle;
    private String myTitleInfo;
    private boolean myCollapsedInitially;
    private boolean myHideHeader;
    private boolean myTrackSelectable;

    private Builder() {
      myTitle = "";
      myTitleInfo = null;
      myCollapsedInitially = false;
      myHideHeader = false;
      myTrackSelectable = false;
    }

    /**
     * @param title string to be displayed in the header
     */
    public Builder setTitle(String title) {
      myTitle = title;
      return this;
    }

    /**
     * @param titleInfo string to be displayed as tooltip next to the header.
     */
    public Builder setTitleInfo(@Nullable String titleInfo) {
      myTitleInfo = titleInfo;
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

    public Builder setTrackSelectable(boolean trackSelectable) {
      myTrackSelectable = trackSelectable;
      return this;
    }

    public TrackGroupModel build() {
      return new TrackGroupModel(this);
    }
  }
}
