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
package com.android.tools.idea.widget

import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

class AdbConnectionWidgetFactory : StatusBarWidgetFactory {
  override fun getId(): String = "AdbConnectionWidget"

  override fun getDisplayName(): @NlsContexts.ConfigurableName String = "ADB Connection"

  override fun isAvailable(project: Project): Boolean {
    return StudioFlags.ADB_CONNECTION_STATUS_WIDGET_ENABLED.get()
  }

  override fun createWidget(project: Project): StatusBarWidget {
    return AdbConnectionWidget(StudioAdapter(project))
  }

  override fun disposeWidget(widget: StatusBarWidget) {
    Disposer.dispose(widget)
  }

  override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}
