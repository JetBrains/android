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

/**
 * An enumerated type of different insertion events, such as an insertion from a
 * copy/paste operation or as the first half of a move operation.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
// TODO: Combine with DragType
public enum InsertType {
  /** The component is newly created (by for example a palette drag) */
  CREATE,

  /**
   * Same as {@link #CREATE} but when the components are constructed for previewing, for
   * example as part of a palette static image render, or a palette drag initial
   * drag preview image. (This is useful to for example skip interactive operations,
   * such as querying the user to choose a drawable resource for an ImageButton
   */
  CREATE_PREVIEW,

  /** The component is being inserted here because it was moved from somewhere else within
   * the same model */
  MOVE_WITHIN,

  /** The component is being inserted here because it was moved from some other model */
  MOVE_INTO,

  /**
   * The component is being inserted here as a result of a copy/paste from elsewhere
   */
  PASTE,

  /**
   * The component is being inserted here as a result of a move with a modifier key.
   * This is functionally the same as a PASTE (with a different user feedback)
   */
  COPY,

  /**
   * The view is created programmatically
   */
  PROGRAMMATIC;

  /**
   * Returns true if this insert type is for a newly created component (for example by a
   * palette drag). Note that this includes both normal create events as well as well as
   * components created as part of previewing operations.
   *
   * @return true if this {@link InsertType} is for a newly created view
   */
  public boolean isCreate() {
    return this == CREATE || this == CREATE_PREVIEW;
  }

  /**
   * Returns true if this insert type is from a move within the current model.
   * Moves from other models is considered a COPY.
   *
   * @return true if this {@link InsertType} is a move
   */
  public boolean isMove() {
    return this == MOVE_WITHIN || this == MOVE_INTO;
  }

  /**
   * Return the {@link DragType} this insert type correspond to.
   * The drag type will normally be used as the command name for a write transaction.
   */
  public DragType getDragType() {
    switch (this) {
      case CREATE:
      case CREATE_PREVIEW:
      case PROGRAMMATIC:
        return DragType.CREATE;
      case MOVE_INTO:
      case MOVE_WITHIN:
        return DragType.MOVE;
      case COPY:
        return DragType.COPY;
      case PASTE:
      default:
        return DragType.PASTE;
    }
  }
}
