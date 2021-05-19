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
import com.android.tools.adtui.model.BoxSelectionModel;
import com.android.tools.adtui.model.DragAndDropListModel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Data model for TrackGroup, a collapsible UI component that contains a list of Tracks.
 */
public class TrackGroupModel extends DragAndDropListModel<TrackModel> {
  /**
   * Use this to generate unique (within this group) IDs for {@link TrackModel}s in this group.
   */
  private static final AtomicInteger TRACK_ID_GENERATOR = new AtomicInteger();

  private final String myTitle;
  @Nullable private final String myTitleHelpText;
  @Nullable private final String myTitleHelpLinkText;
  @Nullable private final String myTitleHelpLinkUrl;
  private final boolean myCollapsedInitially;
  private final boolean myHideHeader;
  private final boolean myTrackSelectable;
  @Nullable private final BoxSelectionModel myBoxSelectionModel;

  private final AspectObserver myObserver = new AspectObserver();
  private final List<TrackGroupActionListener> myActionListeners = new ArrayList<>();

  /**
   * Use builder to instantiate this class.
   */
  private TrackGroupModel(Builder builder) {
    myTitle = builder.myTitle;
    myTitleHelpText = builder.myTitleHelpText;
    myTitleHelpLinkText = builder.myTitleHelpLinkText;
    myTitleHelpLinkUrl = builder.myTitleHelpLinkUrl;
    myCollapsedInitially = builder.myCollapsedInitially;
    myHideHeader = builder.myHideHeader;
    myTrackSelectable = builder.myTrackSelectable;
    myBoxSelectionModel = builder.myBoxSelectionModel;
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
  public String getTitleHelpText() {
    return myTitleHelpText;
  }

  @Nullable
  public String getTitleHelpLinkText() {
    return myTitleHelpLinkText;
  }

  @Nullable
  public String getTitleHelpLinkUrl() {
    return myTitleHelpLinkUrl;
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

  /**
   * @return model for box selection. Null if box selection is not supported.
   */
  @Nullable
  public BoxSelectionModel getBoxSelectionModel() {
    return myBoxSelectionModel;
  }

  /**
   * Add {@link TrackGroupActionListener} to be fired when a track group action, e.g. moving up, is performed.
   */
  public void addActionListener(@NotNull TrackGroupActionListener actionListener) {
    myActionListeners.add(actionListener);
  }

  @NotNull
  public List<TrackGroupActionListener> getActionListeners() {
    return myActionListeners;
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static class Builder {
    private String myTitle;
    @Nullable private String myTitleHelpText;
    @Nullable private String myTitleHelpLinkText;
    @Nullable private String myTitleHelpLinkUrl;
    private boolean myCollapsedInitially;
    private boolean myHideHeader;
    private boolean myTrackSelectable;
    @Nullable private BoxSelectionModel myBoxSelectionModel;

    private Builder() {
      myTitle = "";
      myTitleHelpText = null;
      myTitleHelpLinkText = null;
      myTitleHelpLinkUrl = null;
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
     * @param titleHelpText string to be displayed as tooltip next to the header. Supports HTML tags.
     */
    public Builder setTitleHelpText(@NotNull String titleHelpText) {
      myTitleHelpText = titleHelpText;
      return this;
    }

    /**
     * A link to be displayed as tooltip next after the help text.
     *
     * @param titleHelpLinkText link text
     * @param titleHelpLinkUrl  link URL
     */
    public Builder setTitleHelpLink(@NotNull String titleHelpLinkText, @NotNull String titleHelpLinkUrl) {
      myTitleHelpLinkText = titleHelpLinkText;
      myTitleHelpLinkUrl = titleHelpLinkUrl;
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

    public Builder setBoxSelectionModel(BoxSelectionModel rangeSelectionModel) {
      myBoxSelectionModel = rangeSelectionModel;
      return this;
    }

    public TrackGroupModel build() {
      return new TrackGroupModel(this);
    }
  }
}
