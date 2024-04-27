/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.common.surface;

import com.android.tools.adtui.common.SwingCoordinate;
import org.intellij.lang.annotations.JdkConstants.InputEventMask;

/**
 * An interaction is a mouse or keyboard driven user operation, such as a
 * swipe-select or a resize. It can be thought of as a session, since it is
 * initiated, updated during user manipulation, and finally completed or
 * canceled. An interaction is associated with a single undo transaction (although
 * some interactions don't actually edit anything, such as a selection), and an
 * interaction can have a number of graphics {@link Layer}s which are added and
 * cleaned up on behalf of the interaction by the system.
 * <p/>
 * Interactions are typically mouse oriented. There are pros and cons to using native drag
 * &amp; drop, so various interactions will differ in whether they use it.
 * In particular, you should use drag &amp; drop if your interaction should:
 * <ul>
 * <li> Show a native drag &amp; drop cursor
 * <li> Copy or move data, especially if this applies outside the canvas
 *    control window or even the application itself
 * </ul>
 * You might want to avoid using native drag &amp; drop if your interaction should:
 * <ul>
 * <li> Continue updating itself even when the mouse cursor leaves the
 *    canvas window (in a drag &amp; drop interaction, as soon as you leave the canvas
 *    the drag source is no longer informed of mouse updates, whereas a regular
 *    mouse listener is)
 * <li> Respond to modifier keys (for example, if toggling the Shift key
 *    should constrain motion as is common during resizing, and so on)
 * <li> Use no special cursor (for example, during a marquee selection interaction we
 *     don't want a native drag &amp; drop cursor)
 *  </ul>
 * <p/>
 * Examples of interactions:
 * <ul>
 * <li>Move (dragging to reorder or change hierarchy of views or change visual
 * layout attributes)
 * <li>Marquee (swiping out a rectangle to make a selection)
 * <li>Resize (dragging some edge or corner of a widget to change its size, for
 * example to some new fixed size, or to "attach" it to some other edge.)
 * <li>Inline Editing (editing the text of some text-oriented widget like a
 * label or a button)
 * <li>Link (associate two or more widgets in some way, such as an
 *   "is required" widget linked to a text field)
 * </ul>
 *
 * TODO(b/142953949): Remove this class and just use Interaction2
 */
public abstract class Interaction implements Interaction2 {
  /** Start mouse coordinate, in Swing coordinates */
  @SwingCoordinate protected int myStartX;

  /** Start mouse coordinate, in Swing coordinates */
  @SwingCoordinate protected int myStartY;

  /** Initial AWT mask when the interaction started. */
  @InputEventMask protected int myStartMask;

  /**
   * Handles initialization of this interaction. Called when the interaction is
   * starting.
   *
   * @param x           The most recent mouse x coordinate applicable to this interaction
   * @param y           The most recent mouse y coordinate applicable to this interaction
   * @param modifiersEx The initial AWT mask for the interaction, if known, otherwise 0.
   * @deprecated Use {@link #begin(InteractionEvent)} instead
   */
  @Deprecated
  public void begin(@SwingCoordinate int x, @SwingCoordinate int y, @InputEventMask int modifiersEx) {
    myStartX = x;
    myStartY = y;
    myStartMask = modifiersEx;
  }

  /**
   * Handles updating of the interaction state for a new mouse position.
   *
   * @param x           The most recent mouse x coordinate applicable to this interaction
   * @param y           The most recent mouse y coordinate applicable to this interaction
   * @param modifiersEx current modifier key mask
   * @deprecated Use {@link #update(InteractionEvent)} instead
   */
  @Deprecated
  public void update(@SwingCoordinate int x, @SwingCoordinate int y, @InputEventMask int modifiersEx) {
  }

  /**
   * Handles termination of the interaction. This method is called when the
   * interaction has successful completion.
   *
   * @param x           The most recent mouse x coordinate applicable to this interaction
   * @param y           The most recent mouse y coordinate applicable to this interaction
   * @param modifiersEx current modifier key masks
   * @deprecated Use {@link #commit(InteractionEvent)} instead.
   */
  @Deprecated
  public void end(@SwingCoordinate int x, @SwingCoordinate int y, @InputEventMask int modifiersEx) {
  }
}
