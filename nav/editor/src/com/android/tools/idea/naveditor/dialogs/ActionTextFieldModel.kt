/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.naveditor.dialogs

import com.android.tools.adtui.model.stdui.DefaultCommonTextFieldModel
import com.android.tools.adtui.model.stdui.EditingSupport
import com.android.tools.adtui.model.stdui.EditorCompletion
import com.intellij.openapi.module.Module
import com.android.tools.sdk.AndroidTargetData
import org.jetbrains.android.sdk.getInstance

class ActionTextFieldModel : DefaultCommonTextFieldModel("", "e.g. ACTION_SEND") {
  private lateinit var actions: List<String>

  fun populateCompletions(module: Module) {
    val platform = getInstance(module) ?: return
    val targetData = AndroidTargetData.get(platform.sdkData, platform.target)
    val activityActions = targetData.staticConstantsData.activityActions ?: return
    actions = activityActions
      .filter { it.startsWith("android.intent.action.") }
      .map { "ACTION_${it.substringAfterLast('.')}" }
      .sorted()
  }

  override val editingSupport = object : EditingSupport {
    override val completion: EditorCompletion = { actions }
  }
}
