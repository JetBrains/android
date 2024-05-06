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
package com.android.tools.idea.editors.liveedit.ui

import com.android.tools.idea.editors.liveedit.LiveEditService.Companion.LiveEditTriggerMode.ON_HOTKEY
import com.android.tools.idea.editors.liveedit.LiveEditService.Companion.LiveEditTriggerMode.ON_SAVE
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration.LiveEditMode.LIVE_EDIT
import com.intellij.ide.actionsOnSave.ActionOnSaveContext
import com.intellij.ide.actionsOnSave.ActionOnSaveInfo
import com.intellij.ide.actionsOnSave.ActionOnSaveInfoProvider
import com.intellij.openapi.util.Key
import com.intellij.ui.components.ActionLink

class LiveEditOnSaveInfo(context: ActionOnSaveContext) : ActionOnSaveInfo(context) {
  private val liveEditUiKey : Key<Boolean> = Key("Live Edit On Save Info")

  override fun apply() {
    val ui = isActionOnSaveEnabled
    if (ui != settingInConfigurable()) {
      LiveEditApplicationConfiguration.getInstance().let {
        it.mode = LIVE_EDIT
        it.leTriggerMode = if (ui) ON_SAVE else ON_HOTKEY
      }
    }
  }

  override fun isModified(): Boolean {
    // TODO(b/291643741) Replace with the following commented out line when Live Edit settings page can refresh based on Actions on Save settings.
    // isActionOnSaveEnabled != settingInConfigurable()
    return false
  }

  override fun getActionOnSaveName(): String = "Live Edit"

  // Remove the following method when Live Edit settings page can refresh based on Actions on Save settings.
  override fun isSaveActionApplicable(): Boolean {
    return false
  }

  override fun isActionOnSaveEnabled(): Boolean {
    return context.getUserData(liveEditUiKey) ?: settingInConfigurable()
  }

  override fun setActionOnSaveEnabled(enabled: Boolean) {
    context.putUserData(liveEditUiKey, enabled)
  }

  override fun getActionLinks(): MutableList<out ActionLink> {
    return mutableListOf(createGoToPageInSettingsLink(LiveEditConfigurable.ID))
  }

  override fun getActivatedOnDefaultText(): String = getExplicitSaveText()

  private fun settingInConfigurable(): Boolean =
    LiveEditApplicationConfiguration.getInstance().let { it.isLiveEdit && it.leTriggerMode == ON_SAVE }
}

class LiveEditOnSaveInfoProvider : ActionOnSaveInfoProvider() {
  override fun getActionOnSaveInfos(context: ActionOnSaveContext): MutableCollection<out ActionOnSaveInfo> {
    return mutableListOf(LiveEditOnSaveInfo(context))
  }
}