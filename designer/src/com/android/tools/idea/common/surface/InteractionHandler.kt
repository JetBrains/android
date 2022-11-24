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

import com.android.tools.adtui.PANNABLE_KEY
import com.android.tools.adtui.Pannable
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.api.DragType
import com.android.tools.idea.common.editor.DesignToolsSplitEditor
import com.android.tools.idea.common.editor.showPopup
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.model.Coordinates.getAndroidXDip
import com.android.tools.idea.common.model.Coordinates.getAndroidYDip
import com.android.tools.idea.common.model.DnDTransferItem
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities
import com.android.tools.idea.uibuilder.model.NlDropEvent
import com.android.tools.idea.uibuilder.surface.DragDropInteraction
import com.android.tools.idea.uibuilder.surface.PanInteraction
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.pom.Navigatable
import org.intellij.lang.annotations.JdkConstants
import java.awt.Cursor
import java.awt.Toolkit
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTargetDragEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Handles the interaction events of [DesignSurface]. The events are dispatched from [GuiInputHandler].
 */
interface InteractionHandler {

  /**
   * Called when [GuiInputHandler] has a single click event. ([mouseX], [mouseY]) is the clicked point, and [modifiersEx] is the pressed
   * modifiers when mouse is pressed.
   */
  fun createInteractionOnPressed(@SwingCoordinate mouseX: Int,
                                 @SwingCoordinate mouseY: Int,
                                 @JdkConstants.InputEventMask modifiersEx: Int): Interaction?

  /**
   * Called when [GuiInputHandler] has the dragging event and there is no interactive [Interaction]. ([mouseX], [mouseY]) is the position
   * and [modifiersEx] is the pressed modifiers when dragging starts.
   */
  fun createInteractionOnDrag(@SwingCoordinate mouseX: Int,
                              @SwingCoordinate mouseY: Int,
                              @JdkConstants.InputEventMask modifiersEx: Int): Interaction?

  /**
   * Called when user dragging the [java.awt.Component] into [DesignSurface]. For example, dragging a widget from Palette or ComponentTree
   * into [DesignSurface]
   */
  fun createInteractionOnDragEnter(dragEvent: DropTargetDragEvent): Interaction?

  /**
   * Called when [GuiInputHandler] has the mouse wheel scrolling event and there is no active [Interaction].
   */
  fun createInteractionOnMouseWheelMoved(mouseWheelEvent: MouseWheelEvent): Interaction?

  /**
   * Called by [GuiInputHandler] when mouse is released without any active [Interaction].
   */
  fun mouseReleaseWhenNoInteraction(@SwingCoordinate x: Int, @SwingCoordinate y: Int, @JdkConstants.InputEventMask modifiersEx: Int)

  /**
   * Called by [GuiInputHandler] when left mouse is clicked without shift and control (cmd on mac). ([x], [y]) is the clicked point of
   * mouse, and [modifiersEx] is the pressed modifiers when clicked.
   *
   * This event happens when mouse is pressed and released at the same position without any dragging.
   */
  fun singleClick(@SwingCoordinate x: Int, @SwingCoordinate y: Int, @JdkConstants.InputEventMask modifiersEx: Int)

  /**
   * Called by [GuiInputHandler] when left mouse is double clicked (even the shift or control (cmd on mac) is pressed). ([x], [y]) is the
   * clicked point of mouse, and [modifiersEx] is the pressed modifiers when clicking.
   *
   * This event happens when mouse is pressed and released at the same position without any dragging, before this event is triggered the
   * [singleClick] will be triggered first.
   */
  fun doubleClick(@SwingCoordinate x: Int, @SwingCoordinate y: Int, @JdkConstants.InputEventMask modifiersEx: Int)


  /**
   * Called by [GuiInputHandler] when a zooming event happens.
   */
  fun zoom(type: ZoomType, mouseX: Int, mouseY: Int)

  /**
   * Called when [GuiInputHandler] has no active [Interaction] but mouse is moved. ([mouseX], [mouseY]) is the mouse position , and
   * [modifiersEx] is the pressed modifiers when mouse moves.
   */
  fun hoverWhenNoInteraction(@SwingCoordinate mouseX: Int,
                             @SwingCoordinate mouseY: Int,
                             @JdkConstants.InputEventMask modifiersEx: Int)

  /**
   * Called by [GuiInputHandler] when mouse doesn't move for [GuiInputHandler.HOVER_DELAY_MS] milliseconds. This function does not
   * repeat even the mouse is still not moving.
   */
  fun stayHovering(@SwingCoordinate mouseX: Int, @SwingCoordinate mouseY: Int)

  /**
   * Called by [GuiInputHandler] when the popup context menu event is triggered (e.g. right click on a component). Note that the event
   * may be triggered by different mouse events in different platforms. For example, on Mac and Linux, this event is triggered when
   * **pressing** right mouse button on [DesignSurface]. On Windows, this event is triggered when **releasing** right mouse button.
   */
  fun popupMenuTrigger(mouseEvent: MouseEvent)

  /**
   * Get Cursor by [GuiInputHandler] when there is no active [Interaction].
   */
  fun getCursorWhenNoInteraction(@SwingCoordinate mouseX: Int,
                                 @SwingCoordinate mouseY: Int,
                                 @JdkConstants.InputEventMask modifiersEx: Int): Cursor?

  /**
   * Called by [GuiInputHandler] when a key is pressed without any active [Interaction]. Return an [Interaction] if pressing the given
   * key should start it, or null otherwise.
   */
  fun keyPressedWithoutInteraction(keyEvent: KeyEvent): Interaction?

  /**
   * Called by [GuiInputHandler] when a key is released without any active [Interaction].
   */
  fun keyReleasedWithoutInteraction(keyEvent: KeyEvent)

  /**
   * Called by [GuiInputHandler] when the mouse exits the [DesignSurface]
   */
  fun mouseExited()
}

abstract class InteractionHandlerBase(private val surface: DesignSurface<*>) : InteractionHandler {
  private var cursorWhenNoInteraction: Cursor? = null

  override fun createInteractionOnDragEnter(dragEvent: DropTargetDragEvent): Interaction? {
    val event = NlDropEvent(dragEvent)
    val location = dragEvent.location
    val mouseX = location.x
    val mouseY = location.y
    val sceneView = surface.getSceneViewAtOrPrimary(mouseX, mouseY)
    if (sceneView == null) {
      event.reject()
      return null
    }

    val model = sceneView.sceneManager.model
    val item = DnDTransferItem.getTransferItem(event.getTransferable(), true /* allow placeholders */)
    if (item == null) {
      event.reject()
      return null
    }
    val dragType = if (event.dropAction == DnDConstants.ACTION_COPY) DragType.COPY else DragType.MOVE
    val insertType = model.determineInsertType(dragType, item, true /* preview */)

    val dragged: List<NlComponent>
    if (StudioFlags.NELE_DRAG_PLACEHOLDER.get() && !item.isFromPalette) {
      // When dragging from ComponentTree, it should reuse the existing NlComponents rather than creating the new ones.
      // This impacts some Handlers, using StudioFlag to protect for now.
      // Most of Handlers should be removed once this flag is removed.
      dragged = ArrayList<NlComponent>(surface.selectionModel.selection)
    }
    else {
      if (item.isFromPalette) {
        // remove selection when dragging from Palette.
        surface.selectionModel.clear()
      }
      dragged = model.createComponents(item, insertType)
    }

    if (dragged.isEmpty()) {
      event.reject()
      return null
    }

    val interaction = DragDropInteraction(surface, dragged)
    interaction.setType(dragType)
    interaction.setTransferItem(item)
    // This determines the icon presented to the user while dragging.
    // If we are dragging a component from the palette then use the icon for a copy, otherwise show the icon
    // that reflects the users choice i.e. controlled by the modifier key.
    event.accept(insertType)
    return interaction
  }

  override fun mouseReleaseWhenNoInteraction(@SwingCoordinate x: Int,
                                             @SwingCoordinate y: Int,
                                             @JdkConstants.InputEventMask modifiersEx: Int) {
    val allowToggle = modifiersEx and (InputEvent.SHIFT_MASK or Toolkit.getDefaultToolkit().menuShortcutKeyMask) != 0
    surface.getSceneViewAtOrPrimary(x, y)?.selectComponentAt(x, y, modifiersEx, allowToggle, false)
  }

  override fun zoom(type: ZoomType, mouseX: Int, mouseY: Int) {
    surface.zoom(type, mouseX, mouseY)
  }

  override fun hoverWhenNoInteraction(@SwingCoordinate mouseX: Int,
                                      @SwingCoordinate mouseY: Int,
                                      @JdkConstants.InputEventMask modifiersEx: Int) {
    val sceneView = surface.getSceneViewAtOrPrimary(mouseX, mouseY)
    if (sceneView != null) {
      val context = sceneView.context
      context.setMouseLocation(mouseX, mouseY)
      sceneView.scene.mouseHover(context, getAndroidXDip(sceneView, mouseX), getAndroidYDip(sceneView, mouseY), modifiersEx)
      cursorWhenNoInteraction = sceneView.scene.mouseCursor
    }
    else {
      cursorWhenNoInteraction = null
    }

    surface.sceneManagers.map { it.sceneView }.forEach { it.onHover(mouseX, mouseY) }
  }

  override fun stayHovering(mouseX: Int, mouseY: Int) {
    surface.onHover(mouseX, mouseY)
  }

  override fun popupMenuTrigger(mouseEvent: MouseEvent) {
    val x = mouseEvent.x
    val y = mouseEvent.y
    val modifiersEx = mouseEvent.modifiersEx
    val sceneView = surface.getSceneViewAtOrPrimary(x, y)
    if (sceneView != null) {
      val component = sceneView.selectComponentAt(x, y, modifiersEx, false, true)
      val actions = surface.actionManager.getPopupMenuActions(component)
      // TODO (b/151315668): extract the hardcoded value "LayoutEditor". Be aware this value is used by [SetZoomAction#update].
      surface.showPopup(mouseEvent, actions, "LayoutEditor")
    }
  }

  override fun createInteractionOnMouseWheelMoved(mouseWheelEvent: MouseWheelEvent): Interaction? {
    val x = mouseWheelEvent.x
    val y = mouseWheelEvent.y
    val sceneView = surface.getSceneViewAtOrPrimary(x, y) ?: return null
    val component = Coordinates.findComponent(sceneView, x, y) ?: return null // There is no component consuming the scroll
    return ScrollInteraction.createScrollInteraction(sceneView, component)
  }

  override fun singleClick(@SwingCoordinate x: Int, @SwingCoordinate y: Int, @JdkConstants.InputEventMask modifiersEx: Int) {
    val selectedEditor = FileEditorManager.getInstance(surface.project).selectedEditor
    if (selectedEditor is DesignToolsSplitEditor) {
      val splitEditor = selectedEditor as DesignToolsSplitEditor?
      // We want the code editor scroll position to be modified,
      // even if we are in design mode, and it is not visible at the moment.
      val sceneView = surface.getSceneViewAtOrPrimary(x, y) ?: return
      // TODO: Use {@link SceneViewHelper#selectComponentAt() instead.
      val component = Coordinates.findComponent(sceneView, x, y)
      if (component != null) {
        navigateToComponent(component, false)
      }
    }
  }

  override fun doubleClick(@SwingCoordinate x: Int, @SwingCoordinate y: Int, @JdkConstants.InputEventMask modifiersEx: Int) {
    val sceneView = surface.getSceneViewAtOrPrimary(x, y) ?: return

    // TODO: Use {@link SceneViewHelper#selectComponentAt() instead.
    val component = Coordinates.findComponent(sceneView, x, y)
    if (component != null) {
      // Notify that the user is interested in a component.
      // A properties manager may move the focus to the most important attribute of the component.
      // Such as the text attribute of a TextView
      surface.notifyComponentActivate(component, Coordinates.getAndroidX(sceneView, x), Coordinates.getAndroidY(sceneView, y))
    }
  }

  override fun getCursorWhenNoInteraction(@SwingCoordinate mouseX: Int,
                                          @SwingCoordinate mouseY: Int,
                                          @JdkConstants.InputEventMask modifiersEx: Int): Cursor? {
    return cursorWhenNoInteraction
  }

  override fun keyPressedWithoutInteraction(keyEvent: KeyEvent): Interaction? {
    val keyCode = keyEvent.keyCode
    if (keyCode == DesignSurfaceShortcut.PAN.keyCode) {
      return PanInteraction(surface.getData(PANNABLE_KEY.name) as? Pannable ?: surface)
    }

    // The deletion only applies without modifier keys.
    if (keyEvent.isAltDown || keyEvent.isMetaDown || keyEvent.isShiftDown || keyEvent.isControlDown) {
      return null
    }

    if (keyCode == KeyEvent.VK_DELETE || keyCode == KeyEvent.VK_BACK_SPACE) {
      // Try to delete selected Constraints first.
      if (!ConstraintComponentUtilities.clearSelectedConstraint(surface)) {
        // If there is no Constraint to delete, delete the selected NlComponent(s).
        val selection: List<NlComponent> = surface.selectionModel.selection
        // It is possible that different NlComponents are form different NlModels, group them first.
        val modelComponentsMap = selection.groupBy { it.model }

        // Use WriteCommandAction to wrap deletions so this operation only has one undo stack.
        WriteCommandAction.runWriteCommandAction(surface.project, "Delete Components", null, {
          modelComponentsMap.forEach { (model, nlComponents) -> model.delete(nlComponents) }
        }, *modelComponentsMap.keys.map { it.file }.toTypedArray())
      }
    }
    return null
  }

  override fun keyReleasedWithoutInteraction(keyEvent: KeyEvent) = Unit

  override fun mouseExited() {
    // Call onHover on each SceneView, with coordinates that are sure to be outside.
    surface.sceneManagers.map { it.sceneView }.forEach {
      it.scene.mouseHover(it.context, Int.MIN_VALUE, Int.MIN_VALUE, 0)
      it.onHover(Int.MIN_VALUE, Int.MIN_VALUE)
    }
  }
}

fun navigateToComponent(component: NlComponent, needsFocusEditor: Boolean) {
  val componentBackend = component.backend
  val element = (if (componentBackend.tag == null) null else componentBackend.tag!!.navigationElement) ?: return
  if (PsiNavigationSupport.getInstance().canNavigate(element) && element is Navigatable) {
    (element as Navigatable).navigate(needsFocusEditor)
  }
}

/**
 * [GuiInputHandler] that ignores all interactions.
 */
object NopInteractionHandler: InteractionHandler {
  override fun createInteractionOnPressed(mouseX: Int, mouseY: Int, modifiersEx: Int): Interaction? = null
  override fun createInteractionOnDrag(mouseX: Int, mouseY: Int, modifiersEx: Int): Interaction? = null

  override fun createInteractionOnDragEnter(dragEvent: DropTargetDragEvent): Interaction?  = null
  override fun createInteractionOnMouseWheelMoved(mouseWheelEvent: MouseWheelEvent): Interaction?  = null
  override fun mouseReleaseWhenNoInteraction(x: Int, y: Int, modifiersEx: Int) {}
  override fun singleClick(x: Int, y: Int, modifiersEx: Int) {}
  override fun doubleClick(x: Int, y: Int, modifiersEx: Int) {}
  override fun zoom(type: ZoomType, x: Int, y: Int) {}
  override fun hoverWhenNoInteraction(mouseX: Int, mouseY: Int, modifiersEx: Int) {}
  override fun stayHovering(mouseX: Int, mouseY: Int) {}

  override fun popupMenuTrigger(mouseEvent: MouseEvent) {}
  override fun getCursorWhenNoInteraction(mouseX: Int, mouseY: Int, modifiersEx: Int): Cursor? = null
  override fun keyPressedWithoutInteraction(keyEvent: KeyEvent): Interaction? = null
  override fun keyReleasedWithoutInteraction(keyEvent: KeyEvent) {}
  override fun mouseExited() {}
}

/**
 * An [InteractionHandler] that allows delegating the operations to another [InteractionHandler]. The [delegate] can be switched at runtime
 * and the switch is thread-safe.
 */
class DelegateInteractionHandler(initialDelegate: InteractionHandler = NopInteractionHandler): InteractionHandler {
  private val delegateLock = ReentrantReadWriteLock()
  var delegate: InteractionHandler = initialDelegate
    get() = delegateLock.read { field }
    set(value) = delegateLock.write { field = value}

  override fun createInteractionOnPressed(mouseX: Int, mouseY: Int, modifiersEx: Int): Interaction? =
    delegate.createInteractionOnPressed(mouseX, mouseY, modifiersEx)

  override fun createInteractionOnDrag(mouseX: Int, mouseY: Int, modifiersEx: Int): Interaction? =
    delegate.createInteractionOnDrag(mouseX, mouseY, modifiersEx)

  override fun createInteractionOnDragEnter(dragEvent: DropTargetDragEvent): Interaction? =
    delegate.createInteractionOnDragEnter(dragEvent)

  override fun createInteractionOnMouseWheelMoved(mouseWheelEvent: MouseWheelEvent): Interaction? =
    delegate.createInteractionOnMouseWheelMoved(mouseWheelEvent)

  override fun zoom(type: ZoomType, mouseX: Int, mouseY: Int) =
    delegate.zoom(type, mouseX, mouseY)

  override fun mouseReleaseWhenNoInteraction(x: Int, y: Int, modifiersEx: Int) =
    delegate.mouseReleaseWhenNoInteraction(x, y, modifiersEx)

  override fun stayHovering(mouseX: Int, mouseY: Int) =
    delegate.stayHovering(mouseX, mouseY)

  override fun singleClick(x: Int, y: Int, modifiersEx: Int) =
    delegate.singleClick(x, y, modifiersEx)

  override fun doubleClick(x: Int, y: Int, modifiersEx: Int) =
    delegate.doubleClick(x, y, modifiersEx)

  override fun hoverWhenNoInteraction(mouseX: Int, mouseY: Int, modifiersEx: Int) =
    delegate.hoverWhenNoInteraction(mouseX, mouseY, modifiersEx)

  override fun popupMenuTrigger(mouseEvent: MouseEvent) =
    delegate.popupMenuTrigger(mouseEvent)

  override fun getCursorWhenNoInteraction(mouseX: Int, mouseY: Int, modifiersEx: Int): Cursor? =
    delegate.getCursorWhenNoInteraction(mouseX, mouseY, modifiersEx)

  override fun keyPressedWithoutInteraction(keyEvent: KeyEvent): Interaction? =
    delegate.keyPressedWithoutInteraction(keyEvent)

  override fun keyReleasedWithoutInteraction(keyEvent: KeyEvent) =
    delegate.keyReleasedWithoutInteraction(keyEvent)

  override fun mouseExited() =
    delegate.mouseExited()
}