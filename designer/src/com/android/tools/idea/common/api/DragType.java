/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.common.api;

import org.jetbrains.annotations.NotNull;

/**
 * Describes different types of drag modes. The semantics of how a drop is handled
 * can vary:
 * <ul>
 * <li>Create. This is typically what happens when dragging from the palette;
 * a new copy of the component is created.</li>
 * <li>Copy. This is typically what happens when dragging with a modifier key;
 * a new copy of the components are created.</li>
 * <li>Move. This is typically done by dragging one or more widgets around in
 * the canvas; when moving the widget within a single parent, it may only
 * translate into some updated layout parameters or widget reordering, whereas
 * when moving from one parent to another widgets are moved in the hierarchy
 * as well. This is also used for dragging widgets from one designer to another;
 * then a new copy of the components are created.</li>
 * <li>Paste. Is similar to a copy. It typically tries to preserve internal
 * relationships and id's when possible. If you for example select 3 widgets and
 * cut them, if you paste them the widgets will come back in the exact same place
 * with the same id's. If you paste a second time, the widgets will now all have
 * new unique id's (and any internal references to each other are also updated.)</li>
 * </ul>
 */
public enum DragType {
  /** Create the dragged components */
  CREATE("Create"),
  /** Copy the dragged components */
  COPY("Copy"),
  /** Move the dragged components */
  MOVE("Move"),
  /** Copy the dragged components with paste semantics */
  PASTE("Paste");

  private final String myDescription;

  DragType(String description) {
    myDescription = description;
  }

  /** Returns a description of the drag type */
  public String getDescription(@NotNull String componentType) {
    if (componentType.isEmpty()) {
      componentType = "Components";
    }
    return myDescription + " " + componentType;
  }
}
