/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.common.actions

import com.android.tools.idea.common.model.ItemTransferable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.ide.CopyPasteManager

class PasteWithNewIdsAction : PasteWithIdOptionAction(withNewIds = true)

class PasteWithExistingIdsAction : PasteWithIdOptionAction(withNewIds = false)

abstract class PasteWithIdOptionAction(private val withNewIds: Boolean) : AnAction() {
  companion object {
    @JvmStatic val PASTE_WITH_NEW_IDS_KEY: DataKey<Boolean> = DataKey.create("create_new_ids")
  }

  private var loadedPasteAction: AnAction? = null
  private val pasteAction: AnAction?
    get() {
      if (loadedPasteAction == null) {
        loadedPasteAction = ActionManager.getInstance().getAction(IdeActions.ACTION_PASTE)
      }
      return loadedPasteAction
    }

  override fun getActionUpdateThread() = ActionUpdateThread.EDT

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabledAndVisible = false
    val transferable = CopyPasteManager.getInstance().contents ?: return
    if (!transferable.isDataFlavorSupported(ItemTransferable.DESIGNER_FLAVOR)) {
      return
    }
    event.presentation.isEnabledAndVisible = pasteAction != null
    pasteAction?.update(event)
  }

  override fun actionPerformed(event: AnActionEvent) {
    val dataContext = CustomizedDataContext.withSnapshot(event.dataContext) { sink ->
      sink[PASTE_WITH_NEW_IDS_KEY] = withNewIds
    }
    pasteAction?.actionPerformed(event.withDataContext(dataContext))
  }
}
