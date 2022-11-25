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
import java.awt.event.ActionEvent

/**
 * An interaction is a mouse or keyboard driven user operation, such as a swipe-select or a resize. It can be thought of as a session, since
 * it is initiated, updated during user manipulation, and finally completed or canceled. An interaction can have at most one undo
 * transaction (some interactions, such as a selection do not have a transaction), and an interaction can have a number of graphics [Layer]s
 * which are added and cleaned up on behalf of the interaction by the system.
 *
 * Unlike [Interaction], [Interaction2] uses [InteractionEvent] as its event interface so all events can be used to started, updated, ended,
 * or canceled. The implementation of [Interaction2] has to cast the received [InteractionEvent] to its acceptable event type such like
 * [KeyTypedEvent], [MousePressedEvent], [MouseWheelEvent], [DragEnterEvent], [DragOverEvent], or [DropEvent] to perform the [begin],
 * [update], [commit], and [cancel] operations. This makes [Interaction2] can handle more than one kind of event. For example, a drag
 * interaction can also handle the [KeyPressedEvent] during dragging. so it can apply different behavior when the modifiers are pressed or
 * released during interacting.
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
   * The [event] provided the most recent event information when the interaction is started. For example, if an interaction is started by a
   * key pressing then the instance of event would be [KeyPressedEvent]. It also provides the information regardless the key in
   * [InteractionEvent.info].
   */
  fun begin(event: InteractionEvent)

  /**
   * Called when the interaction is updated.
   *
   * The [event] provided the most recent event information when the interaction is started. For example, if an interaction is updated by a
   * mouse dragging then the instance of event would be [MouseDraggedEvent]. It also provides the information regardless the mouse in
   * [InteractionEvent.info].
   */
  fun update(event: InteractionEvent)

  /**
   * Called when the interaction is committed.
   *
   * The [event] provided the most recent event information when the interaction is committed. For example, if an interaction is committed
   * because the focused component is changed, then it can find the most recent event information from it.
   */
  fun commit(event: InteractionEvent)

  /**
   * Called when the interaction is canceled. The [event] provided the information when interaction is canceled, such like the last mouse
   * position or modifiers.
   */
  fun cancel(event: InteractionEvent)

  /**
   * Called when [GuiInputHandler] asks for the current cursor during interaction.
   */
  fun getCursor(): Cursor?
}

/**
 * The event from [GuiInputHandler]. [info] provides the last information (e.g. mouse position, key board modification) recorded by
 * [GuiInputHandler].
 */
sealed class InteractionEvent(val info: InteractionInformation)

/**
 * [GuiInputHandler] receives an event but which is not created by input. For example, [DesignSurface] is deactivated so the active
 * [Interaction] needs to be canceled, then [Interaction2.cancel] may receive a [InteractionNonInputEvent].
 */
class InteractionNonInputEvent(info: InteractionInformation): InteractionEvent(info)

/**
 * Same as [InteractionEvent] but also provides [eventObject] field to access the original [EventObject] which is created by Swing framework.
 *
 * TODO(b/142953949): Can we remove [eventObject]?
 */
abstract class InteractionInputEvent<out T: EventObject>(val eventObject: T, info: InteractionInformation): InteractionEvent(info)
class MouseClickEvent(mouseEvent: MouseEvent, info: InteractionInformation): InteractionInputEvent<MouseEvent>(mouseEvent, info)
class MousePressedEvent(mouseEvent: MouseEvent, info: InteractionInformation): InteractionInputEvent<MouseEvent>(mouseEvent, info)
class MouseDraggedEvent(mouseEvent: MouseEvent, info: InteractionInformation): InteractionInputEvent<MouseEvent>(mouseEvent, info)
class MouseMovedEvent(mouseEvent: MouseEvent, info: InteractionInformation): InteractionInputEvent<MouseEvent>(mouseEvent, info)
class MouseReleasedEvent(mouseEvent: MouseEvent, info: InteractionInformation): InteractionInputEvent<MouseEvent>(mouseEvent, info)
class MouseWheelMovedEvent(mouseWheelEvent: MouseWheelEvent, info: InteractionInformation)
  : InteractionInputEvent<MouseWheelEvent>(mouseWheelEvent, info)
/**
 * There is no real mouse wheel stop event in Swing framework. [GuiInputHandler] sent this event when mouse wheel is stopped
 * scrolling for [GuiInputHandler.SCROLL_END_TIME_MS] milliseconds.
 *
 * The type of [eventObject] is [ActionEvent] because the stop scrolling event is triggered by [javax.swing.Timer].
 */
class MouseWheelStopEvent(actionEvent: ActionEvent, info: InteractionInformation): InteractionInputEvent<ActionEvent>(actionEvent, info)
class KeyTypedEvent(keyEvent: KeyEvent, info: InteractionInformation): InteractionInputEvent<KeyEvent>(keyEvent, info)
class KeyPressedEvent(keyEvent: KeyEvent, info: InteractionInformation): InteractionInputEvent<KeyEvent>(keyEvent, info)
class KeyReleasedEvent(keyEvent: KeyEvent, info: InteractionInformation): InteractionInputEvent<KeyEvent>(keyEvent, info)
/**
 * The drag event which dragging a component into [DesignSurface], see [java.awt.dnd.DropTargetListener.dragEnter]
 */
class DragEnterEvent(dropTargetDragEvent: DropTargetDragEvent, info: InteractionInformation)
  : InteractionInputEvent<DropTargetDragEvent>(dropTargetDragEvent, info)
/**
 * The drag event which dragging a component over [DesignSurface], see [java.awt.dnd.DropTargetListener.dragOver]
 */
class DragOverEvent(dropTargetDragEvent: DropTargetDragEvent, info: InteractionInformation)
  : InteractionInputEvent<DropTargetDragEvent>(dropTargetDragEvent, info)
/**
 * The event which user modifies the drop gesture. see [java.awt.dnd.DropTargetListener.dropActionChanged]
 */
class DropActionChangedEvent(dropTargetDragEvent: DropTargetDragEvent, info: InteractionInformation)
  : InteractionInputEvent<DropTargetDragEvent>(dropTargetDragEvent, info)
/**
 * The drag event which dragging ongoing from [DesignSurface], see [java.awt.dnd.DropTargetListener.dragExit]
 */
class DragExistEvent(dropTargetEvent: DropTargetEvent, info: InteractionInformation)
  : InteractionInputEvent<DropTargetEvent>(dropTargetEvent, info)
/**
 * The drop event which dropping a component into [DesignSurface], see [java.awt.dnd.DropTargetListener.drop]
 */
class DropEvent(dropTargetDropEvent: DropTargetDropEvent, info: InteractionInformation)
  : InteractionInputEvent<DropTargetDropEvent>(dropTargetDropEvent, info)

data class InteractionInformation(@SwingCoordinate val x: Int, @SwingCoordinate val y: Int, @InputEventMask val modifiersEx: Int)
