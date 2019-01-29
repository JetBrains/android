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

import com.android.tools.idea.common.api.DragType;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.common.model.AndroidCoordinate;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Handler involved in drag &amp; drop operations. Subclassed and returned by
 * {@link ViewGroupHandler#createDragHandler} for view groups that allow their
 * children to be reconfigured by drag &amp; drop.
 */
public abstract class DragHandler {
  @NotNull protected final ViewEditor editor;
  @NotNull protected final ViewGroupHandler handler;
  @NotNull protected final List<NlComponent> components;
  @NotNull protected SceneComponent layout;
  @NotNull protected DragType type = DragType.COPY;
  @AndroidDpCoordinate protected int startX;
  @AndroidDpCoordinate protected int startY;
  @AndroidDpCoordinate protected int lastX;
  @AndroidDpCoordinate protected int lastY;

  /**
   * Constructs a new drag handler for the given view handler
   *
   * @param editor     the associated IDE editor
   * @param handler    the view group handler that may receive the dragged components
   * @param layout     the layout being dragged over/into
   * @param components the components being dragged
   * @param type       the <b>initial</b> type of drag, which can change along the way
   */
  protected DragHandler(@NotNull ViewEditor editor,
                        @NotNull ViewGroupHandler handler,
                        @NotNull SceneComponent layout,
                        @NotNull List<NlComponent> components,
                        @NotNull DragType type) {
    this.editor = editor;
    this.handler = handler;
    this.layout = layout;
    this.components = components;
    this.type = type;
  }

  /**
   * Sets new drag type. This can happen during a drag (e.g. when the user presses a
   * modifier key.
   *
   * @param type the new type to use
   */
  public void setDragType(@NotNull DragType type) {
    this.type = type;
  }

  /**
   * Aborts a drag in this handler's view
   */
  public void cancel() {
  }

  /**
   * Finishes a drag to the given coordinate
   *
   * @param x         the x coordinate in the Android screen pixel coordinate system
   * @param y         the y coordinate in the Android screen pixel coordinate system
   * @param modifiers the modifier key state
   */
  public void commit(@AndroidCoordinate int x, @AndroidCoordinate int y, int modifiers, @NotNull InsertType insertType) {
    editor.insertChildren(layout.getNlComponent(), components, -1, insertType);
  }

  /**
   * Starts a drag of the given components from the given position
   *
   * @param x         the x coordinate in the Android screen pixel coordinate system
   * @param y         the y coordinate in the Android screen pixel coordinate system
   * @param modifiers the modifier key state
   */
  public void start(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, int modifiers) {
    startX = x;
    startY = y;
  }

  /**
   * Continues a drag of the given components from the given position. Will always come after a call to {@link #start}.
   *
   * @param x         the x coordinate in the Android screen pixel coordinate system
   * @param y         the y coordinate in the Android screen pixel coordinate system
   * @param modifiers the modifier key state
   * @return null if the drag is successful so far, or an empty string (or a short error
   * message describing the problem to be shown to the user) if not
   */
  @Nullable
  public String update(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, int modifiers) {
    lastX = x;
    lastY = y;
    return null;
  }

  /**
   * Paints the drag feedback during the drag &amp; drop operation
   *
   * @param graphics the graphics to buildDisplayList to
   */
  public void paint(@NotNull NlGraphics graphics) {
  }
}