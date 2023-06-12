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
package com.android.tools.idea.appinspection.inspectors.backgroundtask.view

import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.BackgroundTaskInspectorClient
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.EntrySelectionModel
import com.intellij.ui.JBSplitter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.VisibleForTesting

/** View class for the Background Task Inspector Tab. */
class BackgroundTaskInspectorTab(
  client: BackgroundTaskInspectorClient,
  ideServices: AppInspectionIdeServices,
  uiComponentsProvider: UiComponentsProvider,
  scope: CoroutineScope,
  uiDispatcher: CoroutineDispatcher
) {

  @VisibleForTesting val selectionModel = EntrySelectionModel()

  private val entriesView =
    BackgroundTaskEntriesView(this, client, selectionModel, scope, uiDispatcher)
  private val detailsView =
    EntryDetailsView(
      this,
      client,
      ideServices,
      selectionModel,
      entriesView,
      uiComponentsProvider,
      scope,
      uiDispatcher
    )

  var isDetailsViewVisible = false
    set(value) {
      if (value != field) {
        field = value
        splitter.secondComponent = if (value) detailsView else null
      }
    }

  private val splitter =
    JBSplitter(false).apply {
      border = AdtUiUtils.DEFAULT_VERTICAL_BORDERS
      isOpaque = true
      firstComponent = entriesView
      secondComponent = null
      dividerWidth = 1
      divider.background = AdtUiUtils.DEFAULT_BORDER_COLOR
    }

  val component = splitter
}
