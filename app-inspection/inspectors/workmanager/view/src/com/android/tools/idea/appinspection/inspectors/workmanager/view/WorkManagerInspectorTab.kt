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
package com.android.tools.idea.appinspection.inspectors.workmanager.view

import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspectors.workmanager.model.WorkManagerInspectorClient
import com.android.tools.idea.appinspection.inspectors.workmanager.model.WorkSelectionModel
import com.intellij.ui.JBSplitter
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.TestOnly
import javax.swing.JComponent

/**
 * View class for the WorkManger Inspector Tab.
 *
 * The [component] of tab view is essentially a [JBSplitter] with [WorksContentView]
 * on the left and [WorkInfoDetailsView] on the right.
 */
class WorkManagerInspectorTab(client: WorkManagerInspectorClient,
                              ideServices: AppInspectionIdeServices,
                              scope: CoroutineScope
) {
  @TestOnly
  val workSelectionModel = WorkSelectionModel()

  /**
   * Returns true if the [WorkInfoDetailsView] is visible.
   */
  var isDetailsViewVisible: Boolean = false
    set(value) {
      if (value != field) {
        field = value
        splitter.secondComponent = if (value) detailsView else null
        if (value) {
          workSelectionModel.setSelectedWork(workSelectionModel.selectedWork, WorkSelectionModel.Context.TABLE)
          splitter.revalidate()
          splitter.repaint()
        }
      }
    }

  private val contentView = WorksContentView(this, workSelectionModel, client)
  private val detailsView = WorkInfoDetailsView(this, client, ideServices, scope, workSelectionModel, contentView)

  private val splitter = JBSplitter(false).apply {
    border = AdtUiUtils.DEFAULT_VERTICAL_BORDERS
    isOpaque = true
    firstComponent = contentView
  }

  val component: JComponent = splitter
}
