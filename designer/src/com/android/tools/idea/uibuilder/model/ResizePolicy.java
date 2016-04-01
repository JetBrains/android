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
package com.android.tools.idea.uibuilder.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link ResizePolicy} records state for whether a widget is resizable, and if so, in
 * which directions
 * <p/>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
public class ResizePolicy {
  private static final int NONE = 0;
  private static final int LEFT_EDGE = 1;
  private static final int RIGHT_EDGE = 2;
  private static final int TOP_EDGE = 4;
  private static final int BOTTOM_EDGE = 8;
  private static final int PRESERVE_RATIO = 16;

  // Aliases
  private static final int HORIZONTAL = LEFT_EDGE | RIGHT_EDGE;
  private static final int VERTICAL = TOP_EDGE | BOTTOM_EDGE;
  private static final int ANY = HORIZONTAL | VERTICAL;

  // Shared objects for common policies

  private static final ResizePolicy ourAny = new ResizePolicy(ANY);
  private static final ResizePolicy ourNone = new ResizePolicy(NONE);
  private static final ResizePolicy ourHorizontal = new ResizePolicy(HORIZONTAL);
  private static final ResizePolicy ourVertical = new ResizePolicy(VERTICAL);
  private static final ResizePolicy ourScaled = new ResizePolicy(ANY | PRESERVE_RATIO);

  private final int myFlags;


  // Use factory methods to construct
  private ResizePolicy(int flags) {
    myFlags = flags;
  }

  /**
   * Returns true if this policy allows resizing in at least one direction
   *
   * @return true if this policy allows resizing in at least one direction
   */
  public boolean isResizable() {
    return (myFlags & ANY) != 0;
  }

  /**
   * Returns true if this policy allows resizing the top edge
   *
   * @return true if this policy allows resizing the top edge
   */
  public boolean topAllowed() {
    return (myFlags & TOP_EDGE) != 0;
  }

  /**
   * Returns true if this policy allows resizing the right edge
   *
   * @return true if this policy allows resizing the right edge
   */
  public boolean rightAllowed() {
    return (myFlags & RIGHT_EDGE) != 0;
  }

  /**
   * Returns true if this policy allows resizing the bottom edge
   *
   * @return true if this policy allows resizing the bottom edge
   */
  public boolean bottomAllowed() {
    return (myFlags & BOTTOM_EDGE) != 0;
  }

  /**
   * Returns true if this policy allows resizing the left edge
   *
   * @return true if this policy allows resizing the left edge
   */
  public boolean leftAllowed() {
    return (myFlags & LEFT_EDGE) != 0;
  }

  /**
   * Returns true if this policy requires resizing in an aspect-ratio preserving manner
   *
   * @return true if this policy requires resizing in an aspect-ratio preserving manner
   */
  public boolean isAspectPreserving() {
    return (myFlags & PRESERVE_RATIO) != 0;
  }

  /**
   * Returns a resize policy allowing resizing in any direction
   *
   * @return a resize policy allowing resizing in any direction
   */
  @NotNull
  public static ResizePolicy full() {
    return ourAny;
  }

  /**
   * Returns a resize policy not allowing any resizing
   *
   * @return a policy which does not allow any resizing
   */
  @NotNull
  public static ResizePolicy none() {
    return ourNone;
  }

  /**
   * Returns a resize policy allowing horizontal resizing only
   *
   * @return a policy which allows horizontal resizing only
   */
  @NotNull
  public static ResizePolicy horizontal() {
    return ourHorizontal;
  }

  /**
   * Returns a resize policy allowing vertical resizing only
   *
   * @return a policy which allows vertical resizing only
   */
  @NotNull
  public static ResizePolicy vertical() {
    return ourVertical;
  }

  /**
   * Returns a resize policy allowing scaled / aspect-ratio preserving resizing only
   *
   * @return a resize policy allowing scaled / aspect-ratio preserving resizing only
   */
  @NotNull
  public static ResizePolicy scaled() {
    return ourScaled;
  }

  /**
   * Returns a resize policy with the specified resizability along the edges and the
   * given aspect ratio behavior
   *
   * @param top      whether the top edge is resizable
   * @param right    whether the right edge is resizable
   * @param bottom   whether the bottom edge is resizable
   * @param left     whether the left edge is resizable
   * @param preserve whether the policy requires the aspect ratio to be preserved
   * @return a resize policy recording the constraints required by the parameters
   */
  @NotNull
  public static ResizePolicy create(boolean top, boolean right, boolean bottom, boolean left, boolean preserve) {
    int mask = NONE;
    if (top) mask |= TOP_EDGE;
    if (right) mask |= RIGHT_EDGE;
    if (bottom) mask |= BOTTOM_EDGE;
    if (left) mask |= LEFT_EDGE;
    if (preserve) mask |= PRESERVE_RATIO;

    return new ResizePolicy(mask);
  }

  /**
   * Returns the {@link ResizePolicy} for the given policy description.
   *
   * @param resize the string describing the resize policy; one of "full", "none",
   *               "horizontal", "vertical", or "scaled"
   * @return the {@link ResizePolicy} for the widget, which will never be null (but may
   *         be the default of {@link ResizePolicy#full()} if no metadata is found for
   *         the given widget)
   */
  @Nullable
  public static ResizePolicy get(@Nullable String resize) {
    if (resize != null && resize.length() > 0) {
      if ("full".equals(resize)) {
        return full();
      } else if ("none".equals(resize)) {
        return none();
      } else if ("horizontal".equals(resize)) {
        return horizontal();
      } else if ("vertical".equals(resize)) {
        return vertical();
      } else if ("scaled".equals(resize)) {
        return scaled();
      } else {
        assert false : resize;
      }
    }

    return null;
  }

  /**
   * Returns the {@link ResizePolicy} for the given component
   *
   * @param component the component to look up a resize policy for
   * @return a suitable {@linkplain ResizePolicy}
   */
  @NotNull
  public static ResizePolicy getResizePolicy(@NotNull NlComponent component) {
    // TODO: Look up from metadata
    return ourAny;
  }
}
