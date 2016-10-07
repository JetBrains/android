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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.uibuilder.model.AndroidCoordinate;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.SegmentType;

import java.awt.*;

/**
 * Handler involved in resize operations. Subclassed and returned by
 * {@link ViewGroupHandler#createResizeHandler} for views that are resizeable.
 */
public abstract class ResizeHandler {
  @NotNull protected final ViewEditor editor;
  @NotNull protected final ViewGroupHandler handler;
  @NotNull protected final NlComponent layout;
  @NotNull protected final NlComponent component;
  @AndroidCoordinate protected int startX;
  @AndroidCoordinate protected int startY;
  @AndroidCoordinate protected int lastX;
  @AndroidCoordinate protected int lastY;
  protected int lastModifiers;

  /** The type of horizontal edge being resized, or null */
  public SegmentType horizontalEdgeType;

  /** The type of vertical edge being resized, or null */
  public SegmentType verticalEdgeType;

  /**
   * Constructs a new resize handler to resize the given component
   *
   * @param editor             the associated IDE editor
   * @param handler            the view group handler that may receive the dragged components
   * @param component          the component being resized
   * @param horizontalEdgeType the horizontal (top or bottom) edge being resized, if any
   * @param verticalEdgeType   the vertical (left or right) edge being resized, if any
   */
  protected ResizeHandler(@NotNull ViewEditor editor,
                          @NotNull ViewGroupHandler handler,
                          @NotNull NlComponent component,
                          @Nullable SegmentType horizontalEdgeType,
                          @Nullable SegmentType verticalEdgeType) {
    this.editor = editor;
    this.handler = handler;
    NlComponent parent = component.getParent();
    assert parent != null : component; // or we wouldn't have created the resize handler for this component
    this.layout = parent;
    this.component = component;
    this.horizontalEdgeType = horizontalEdgeType;
    this.verticalEdgeType = verticalEdgeType;
  }

  /** Aborts a resize operation in this handler's view */
  public void cancel() {
  }

  /**
   * Finishes a resize at the given coordinate
   *
   * @param x         the x coordinate in the Android screen pixel coordinate system
   * @param y         the y coordinate in the Android screen pixel coordinate system
   * @param modifiers the modifier key state
   * @param newBounds the new (proposed) bounds of the component
   */
  public abstract void commit(@AndroidCoordinate int x,
                              @AndroidCoordinate int y,
                              int modifiers,
                              @NotNull @AndroidCoordinate Rectangle newBounds);

  /**
   * Starts a resize at the given position
   *
   * @param x         the x coordinate in the Android screen pixel coordinate system
   * @param y         the y coordinate in the Android screen pixel coordinate system
   * @param modifiers the modifier key state
   */
  public void start(@AndroidCoordinate int x, @AndroidCoordinate int y, int modifiers) {
    startX = x;
    startY = y;
    lastX = x;
    lastY = y;
    lastModifiers = modifiers;
  }

  /**
   * Continues a resize to the given position. Will always come after a call to {@link #start}.
   *
   * @param x         the x coordinate in the Android screen pixel coordinate system
   * @param y         the y coordinate in the Android screen pixel coordinate system
   * @param modifiers the modifier key state
   * @param newBounds the new (proposed) bounds of the component
   * @return null if the drag is successful so far, or an empty string (or a short error
   * message describing the problem to be shown to the user) if not
   */
  @Nullable
  public String update(@AndroidCoordinate int x, @AndroidCoordinate int y, int modifiers,
                     @NotNull @AndroidCoordinate Rectangle newBounds) {
    lastX = x;
    lastY = y;
    lastModifiers = modifiers;
    return null;
  }

  /**
   * Paints the drag feedback during the resize operation
   */
  public abstract void paint(@NotNull NlGraphics graphics);
}
