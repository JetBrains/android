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
package com.android.tools.inspectors.common.api.ide.stacktrace

import com.android.tools.adtui.stdui.ContextMenuItem
import com.android.tools.inspectors.common.api.ide.IntellijContextMenuInstaller
import com.android.tools.inspectors.common.api.stacktrace.StackTraceModel
import com.android.tools.inspectors.common.ui.ContextMenuInstaller
import com.android.tools.inspectors.common.ui.stacktrace.StackTraceGroup
import com.android.tools.inspectors.common.ui.stacktrace.StackTraceView
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import javax.swing.JList
import javax.swing.event.ListSelectionListener

class IntelliJStackTraceGroup
@VisibleForTesting
internal constructor(
  private val project: Project,
  private val viewGenerator: (Project, StackTraceModel) -> IntelliJStackTraceView,
) : StackTraceGroup {
  private val stackTraceViews: MutableList<IntelliJStackTraceView> = ArrayList()

  constructor(
    project: Project,
    parentDisposable: Disposable,
  ) : this(
    project,
    { p: Project, m: StackTraceModel ->
      val view = IntelliJStackTraceView(p, m, parentDisposable)
      val installer: ContextMenuInstaller = IntellijContextMenuInstaller()
      view.installNavigationContextMenu(installer)
      view.installGenericContextMenu(installer, ContextMenuItem.SEPARATOR)
      // Adds context menu to copy the selected line in the stack trace view. This is needed for
      // users to search a specific line anywhere,
      // specially when the navigation is not available for third party code.
      view.installGenericContextMenu(installer, ContextMenuItem.COPY)
      view
    }
  )

  override fun createStackView(model: StackTraceModel): StackTraceView {
    val view = viewGenerator(project, model)
    stackTraceViews.add(view)

    view.addListSelectionListener(
      ListSelectionListener { e ->
        // Ignore when a view had its selection cleared, since that shouldn't affect other views
        // in the group (and may have even been externally caused by another one)
        if ((e.source as JList<*>).selectedIndex < 0) {
          return@ListSelectionListener
        }
        stackTraceViews.forEach {
          if (it != view) {
            it.clearSelection()
          }
        }
      }
    )

    return view
  }
}
