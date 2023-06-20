/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.compose.pickers.base.editingsupport

import com.android.tools.adtui.model.stdui.EditingSupport
import com.android.tools.adtui.model.stdui.EditingValidation
import com.intellij.openapi.application.ApplicationManager

class PsiEditingSupport(override val validation: EditingValidation) : EditingSupport {
  override val execution = { runnable: Runnable ->
    ApplicationManager.getApplication().executeOnPooledThread(runnable)
  }
  override val uiExecution = { runnable: Runnable ->
    ApplicationManager.getApplication().invokeLater(runnable)
  }
}
