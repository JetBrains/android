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

import com.android.annotations.NonNull;
import com.android.tools.idea.uibuilder.model.AndroidCoordinate;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.surface.ScreenView;

import java.awt.*;
import java.util.List;

/**
 * Handler involved in drag &amp; drop operations. Subclassed and returned by
 * {@link ViewGroupHandler#createDragHandler} for view groups that allow their
 * children to be reconfigured by drag &amp; drop.
 */
public abstract class DragHandler {
  @NonNull protected final ViewEditor editor;
  @NonNull protected final ViewGroupHandler handler;
  @NonNull protected final List<NlComponent> components;
  @NonNull protected final NlComponent layout;
  @NonNull protected DragType type = DragType.COPY;
  @AndroidCoordinate protected int startX;
  @AndroidCoordinate protected int startY;
  @AndroidCoordinate protected int lastX;
  @AndroidCoordinate protected int lastY;

  /**
   * Constructs a new drag handler for the given view handler
   *
   * @param editor     the associated IDE editor
   * @param handler    the view group handler that may receive the dragged components
   * @param layout     the layout being dragged over/into
   * @param components the components being dragged
   * @param type       the <b>initial</b> type of drag, which can change along the way
   */
  protected DragHandler(@NonNull ViewEditor editor,
                        @NonNull ViewGroupHandler handler,
                        @NonNull NlComponent layout,
                        @NonNull List<NlComponent> components,
                        @NonNull DragType type) {
    this.editor = editor;
    this.handler = handler;
    this.layout = layout;
    this.components = components;
    this.type = type;
  }

  /**
   * Sets new drag type. This can happen during a drag (e.g. when the user presses a
   * modifier key.
   * @param type the new type to use
   */
  public void setDragType(@NonNull DragType type) {
    this.type = type;
  }

  /** Aborts a drag in this handler's view */
  public void cancel() {
  }

  /**
   * Finishes a drag to the given coordinate
   *
   * @param x the x coordinate in the Android screen pixel coordinate system
   * @param y the y coordinate in the Android screen pixel coordinate system
   */
  public abstract void commit(@AndroidCoordinate int x, @AndroidCoordinate int y);

  /**
   * Starts a drag of the given components from the given position
   *
   * @param x                 the x coordinate in the Android screen pixel coordinate system
   * @param y                 the y coordinate in the Android screen pixel coordinate system
   */
  public void start(@AndroidCoordinate int x, @AndroidCoordinate int y) {
    startX = x;
    startY = y;
  }

  /**
   * Continues a drag of the given components from the given position. Will always come after a call to {@link #start}.
   *
   * @param x the x coordinate in the Android screen pixel coordinate system
   * @param y the y coordinate in the Android screen pixel coordinate system
   */
  public void update(@AndroidCoordinate int x, @AndroidCoordinate int y) {
    lastX = x;
    lastY = y;
  }

  /**
   * Paints the drag feedback during the drag &amp; drop operation
   */
  public abstract void paint(@NonNull ScreenView screen, @NonNull Graphics2D gc);
}