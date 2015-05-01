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

/**
 * Describes different types of drag modes. The semantics of how a drop is handled
 * can vary:
 * <ul>
 * <li>Copy. This is typically what happens when dragging from the palette;
 * a new copy of the components are created. This can also be achieved when
 * dragging with a modifier key.</li>
 * <li>Move. This is typically done by dragging one or more widgets around in
 * the canvas; when moving the widget within a single parent, it may only
 * translate into some updated layout parameters or widget reordering, whereas
 * when moving from one parent to another widgets are moved in the hierarchy
 * as well.</li>
 * <li>Move within same parent.</li>
 * <li>A paste is similar to a copy. It typically tries to preserve internal
 * relationships and id's when possible. If you for example select 3 widgets and
 * cut them, if you paste them the widgets will come back in the exact same place
 * with the same id's. If you paste a second time, the widgets will now all have
 * new unique id's (and any internal references to each other are also updated.)</li>
 * </ul>
 */
public enum DragType {
  /** Copy the dragged components */
  COPY("Copy Components"),
  /** Move the dragged components */
  MOVE("Move Components"),
  /** Copy the dragged components with paste semantics */
  PASTE("Paste Components");

  private final String myDescription;

  DragType(String description) {
    myDescription = description;
  }

  /** Returns a description of the drag type */
  public String getDescription() {
    return myDescription;
  }
}
