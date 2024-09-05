/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.vitals.ui

import com.android.tools.idea.insights.LoadingState
import com.android.tools.idea.insights.ui.EMPTY_STATE_TEXT_FORMAT
import com.android.tools.idea.insights.ui.EMPTY_STATE_TITLE_FORMAT
import com.android.tools.idea.insights.ui.InsightPermissionDeniedHandler
import com.intellij.util.ui.StatusText

class VitalsInsightPermissionDeniedHandler : InsightPermissionDeniedHandler {
  override fun handlePermissionDenied(
    permissionDenied: LoadingState.PermissionDenied,
    statusText: StatusText,
  ) {
    statusText.apply {
      clear()
      appendText("Request failed", EMPTY_STATE_TITLE_FORMAT)
      appendLine("You do not have permission to fetch insights", EMPTY_STATE_TEXT_FORMAT, null)
    }
  }
}
