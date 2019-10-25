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
package com.android.tools.idea.common.surface

import com.android.tools.adtui.common.SwingCoordinate
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.dnd.DropTargetEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.util.EventObject
import org.intellij.lang.annotations.JdkConstants.InputEventMask
import java.awt.Cursor

/**
 * An interaction is a mouse or keyboard driven user operation, such as a swipe-select or a resize. It can be thought of as a session, since
 * it is initiated, updated during user manipulation, and finally completed or canceled. An interaction can have at most one undo
 * transaction (some interactions, such as a selection do not have a transaction), and an interaction can have a number of graphics [Layer]s
 * which are added and cleaned up on behalf of the interaction by the system.
 *
 * Unlike [Interaction], [Interaction2] uses [EventObject] as event interface so all events can be used to started, updated, ended, or
 * canceled. The implementation of [Interaction2] has to cast the received [EventObject] to its acceptable event such like [KeyEvent],
 * [MouseEvent], [MouseWheelEvent], [DropTargetDragEvent], [DropTargetEvent], or [DropTargetDropEvent] to perform the [begin], [update],
 * [commit], and [cancel] operations. This makes [Interaction2] can handle more than one kind of event. For example, a drag interaction can
 * also handle the [KeyEvent] during dragging. so it can apply different behavior when modifiers is pressed or released during interacting.
 *
 * TODO(b/142953949): Replace [Interaction] with this class.
 */
interface Interaction2 {

  /**
   * Returns a list of overlays, from bottom to top (where the later overlays are painted on top of earlier ones if they overlap).
   * (The first [Layer] is painted first, so the following [Layer]s may overwrite the previous [Layer]s).
   *
   * @return A list of overlays to buildDisplayList for this interaction, if applicable.
   */
  fun createOverlays(): List<Layer>

  /**
   * Called when the interaction is started.
   *
   * Argument [event] may be null if this interaction is started without any events. For example, the action is forced started by an action
   * in [DesignSurface].
   *
   * The [interactionInformation] provided the most recent event information when the interaction is started. For example, if an interaction
   * is started by a [KeyEvent] but it needs to know the current mouse information, then it can find it from [interactionInformation].
   */
  fun begin(event: EventObject?, interactionInformation: InteractionInformation)

  /**
   * Called when the interaction is updated.
   *
   * The [interactionInformation] provided the most recent event information when the interaction is updated. For example, if an interaction
   * is updated by a [KeyEvent] but it needs to know the current mouse information, then it can find it from [interactionInformation].
   */
  fun update(event: EventObject, interactionInformation: InteractionInformation)

  /**
   * Called when the interaction is committed.
   *
   * Argument [event] may be null if this interaction is ended without any events. For example, the editing file is closed so
   * [InteractionManager] is disposed.
   *
   * The [interactionInformation] provided the most recent event information when the interaction is committed. For example, if an
   * interaction is committed because the focused component is changed, then it can find the most recent event information from it.
   */
  fun commit(event: EventObject?, interactionInformation: InteractionInformation)

  /**
   * Called when the interaction is canceled.
   *
   * Argument [event] may be null if this interaction is ended without other event. For example, the editing file is closed so
   * [InteractionManager] is disposed.
   *
   * The [interactionInformation] provided the most recent event information when interaction is canceled. For example, if an interaction is
   * canceled because the file is closed, then it can find the most recent event information from it.
   */
  fun cancel(event: EventObject?, interactionInformation: InteractionInformation)

  /**
   * Called when [InteractionManager] asks for the current cursor during interaction.
   */
  fun getCursor(): Cursor?
}

data class InteractionInformation(@SwingCoordinate val x: Int, @SwingCoordinate val y: Int, @InputEventMask val modifiersEx: Int)
