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
package com.android.tools.property.ptable2.impl

import com.intellij.ui.table.JBTable
import com.intellij.util.IJSwingUtilities
import sun.awt.CausedFocusEvent
import java.awt.KeyboardFocusManager
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.table.TableModel

open class PFormTableImpl(model: TableModel) : JBTable(model) {

  init {
    isFocusTraversalPolicyProvider = true
    focusTraversalPolicy = PTableFocusTraversalPolicy(this)
    super.resetDefaultFocusTraversalKeys()

    super.addFocusListener(object : FocusAdapter() {
      override fun focusGained(event: FocusEvent) {
        // If this table gains focus from focus traversal,
        // and there are editable cells: delegate to the next focus candidate.
        when {
          event !is CausedFocusEvent -> return
          event.cause == CausedFocusEvent.Cause.TRAVERSAL_FORWARD -> transferFocus()
          event.cause == CausedFocusEvent.Cause.TRAVERSAL_BACKWARD -> transferFocusBackward()
        }
      }
    })
  }

  // When an editor is present, do not accept focus on the table itself.
  // This fixes a problem when navigating backwards.
  // The LayoutFocusTraversalPolicy for the container of the table would include
  // the table as the last possible focus component when navigating backwards.
  override fun isFocusable(): Boolean {
    return super.isFocusable() && !isEditing && rowCount > 0
  }

  override fun removeEditor() {
    // b/37132037 Remove focus from the editor before hiding the editor.
    // When we are transferring focus to another cell we will have to remove the current
    // editor. The auto focus transfer in Container.removeNotify will cause another undesired
    // focus event. This is an attempt to avoid that.
    // The auto focus transfer is a common problem for applications see this open bug: JDK-6210779.
    val editor = editorComponent
    if (editor != null && IJSwingUtilities.hasFocus(editor)) {
      KeyboardFocusManager.getCurrentKeyboardFocusManager().clearFocusOwner()
    }
    super.removeEditor()
  }
}
