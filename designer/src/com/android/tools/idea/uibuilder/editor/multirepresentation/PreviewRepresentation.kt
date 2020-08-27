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

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.fileEditor.FileEditor
import javax.swing.JComponent

/**
 * An interface for a generic representation of a preview for a case where a preview part of [com.intellij.openapi.fileEditor.TextEditor]
 * with preview can have several representations.
 *
 * TODO(b/143067434): have a solid motivation for having [updateNotifications] and [registerShortcuts] as part of this interface
 */
interface PreviewRepresentation : Disposable {
  /**
   * Provides a [JComponent] to be displayed in the parent [MultiRepresentationPreview] if this representation is selected.
   */
  val component: JComponent

  /**
   * Used for propagating notification updates events down to the [PreviewRepresentation] from the parent [MultiRepresentationPreview].
   */
  fun updateNotifications(parentEditor: FileEditor) { }

  /**
   * Used in case parent editor wants the shortcuts specific to a particular [PreviewRepresentation] to be applicable in a context outside
   * of the [PreviewRepresentation] (e.g. in the entire editor or its parents).
   */
  fun registerShortcuts(applicableTo: JComponent) { }

  // region Lifecycle handling
  /**
   * Method called by the [MultiRepresentationPreview] when this [PreviewRepresentation] becomes active. This means that this representation
   * is visible or about to become visible. This method will be guaranteed to be called when the representation is added if it's
   * immediately visible.
   */
  fun onActivate() {}

  /**
   * Analogous to [onActivate], called when the representation is hidden or goes to the background. This method can be used to stop certain
   * listeners to avoid doing unnecessary background work.
   * [onActivate] will be called if the representation becomes active again.
   */
  fun onDeactivate() {}
  // endregion

  // region State handling
  /**
   * Called to restore any saved state in [getState] after this preview is loaded. The method will not be called if there is no saved
   * state.
   *
   * This method will only be invoked once after the representation has been instantiated.
   */
  fun setState(state: PreviewRepresentationState) {}

  /**
   * Called to retrieve any saved state for this [PreviewRepresentation].
   */
  fun getState(): PreviewRepresentationState? = null
  // endregion

  // region Text editor caret handling
  /**
   * Called when the caret position changes. This method must return ASAP and not block.
   */
  fun onCaretPositionChanged(event: CaretEvent) {}
  // endregion
}