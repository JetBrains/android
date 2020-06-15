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
}