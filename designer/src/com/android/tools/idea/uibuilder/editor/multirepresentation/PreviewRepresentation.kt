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
package com.android.tools.idea.uibuilder.editor.multirepresentation

import com.android.annotations.concurrency.Slow
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.fileEditor.FileEditor
import javax.swing.JComponent

/**
 * Preferred visibility of a representation. When the container supports it, this will indicate to
 * it whether this representation prefers to be shown (in split mode or not) or hidden on
 * initialization.
 */
enum class PreferredVisibility {
  /**
   * If the representation would prefer to be hidden on initialization. Use this if for example, it
   * is not expected to have content.
   */
  HIDDEN,
  /** If the representation would prefer to be visible in split mode on initialization. */
  SPLIT,
  /** If the representation would prefer to be visible in design mode on initialization. */
  FULL,
}

/**
 * An interface for a generic representation of a preview for a case where a preview part of
 * [com.intellij.openapi.fileEditor.TextEditorWithPreview] can have several representations.
 *
 * TODO(b/143067434): have a solid motivation for having [updateNotifications] and
 *   [registerShortcuts] as part of this interface
 */
interface PreviewRepresentation : Disposable {
  /**
   * Provides a [JComponent] to be displayed in the parent [MultiRepresentationPreview] if this
   * representation is selected.
   */
  val component: JComponent

  /**
   * Optional preferred initial visibility of this editor when opening this representation. If null,
   * the container of this representation will decide.
   */
  val preferredInitialVisibility: PreferredVisibility?

  /**
   * Used for propagating notification updates events down to the [PreviewRepresentation] from the
   * parent [MultiRepresentationPreview].
   */
  fun updateNotifications(parentEditor: FileEditor) {}

  /**
   * Used in case parent editor wants the shortcuts specific to a particular [PreviewRepresentation]
   * to be applicable in a context outside of the [PreviewRepresentation] (e.g. in the entire editor
   * or its parents).
   */
  fun registerShortcuts(applicableTo: JComponent) {}

  /**
   * Returns whether this [PreviewRepresentation] has previews to show.
   *
   * This method is potentially slow, because we might need to read the file to compute if it has
   * previews. Therefore, it shouldn't be called from the UI thread.
   */
  @Slow suspend fun hasPreviews(): Boolean = true

  /**
   * Whether this [PreviewRepresentation] has previews to show. This method should return fast and
   * not block.
   */
  fun hasPreviewsCached() = true

  // region Lifecycle handling
  /**
   * Method called by the [MultiRepresentationPreview] when this [PreviewRepresentation] becomes
   * active. This means that this representation is visible or about to become visible. This method
   * will be guaranteed to be called when the representation is added if it's immediately visible.
   */
  fun onActivate() {}

  /**
   * Analogous to [onActivate], called when the representation is hidden or goes to the background.
   * This method can be used to stop certain listeners to avoid doing unnecessary background work.
   * [onActivate] will be called if the representation becomes active again.
   */
  fun onDeactivate() {}

  // endregion

  // region State handling
  /**
   * Called to restore any saved state in [getState] after this preview is loaded. The method will
   * not be called if there is no saved state.
   *
   * This method will only be invoked once after the representation has been instantiated.
   */
  fun setState(state: PreviewRepresentationState) {}

  /** Called to retrieve any saved state for this [PreviewRepresentation]. */
  fun getState(): PreviewRepresentationState? = null

  // endregion

  // region Text editor caret handling
  /**
   * Called when the caret position changes. This method must return ASAP and not block.
   *
   * @param event the [CaretEvent] being handled.
   * @param isModificationTriggered true if the caret moved because the user modified the file, as
   *   opposed to just navigating with arrows or the mouse.
   */
  fun onCaretPositionChanged(event: CaretEvent, isModificationTriggered: Boolean) {}
  // endregion
}
