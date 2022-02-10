/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.logcat.filters

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.logcat.LogcatPresenter
import com.android.tools.idea.logcat.settings.LogcatSettings
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import java.awt.Component

/**
 * An interface for a UI component containing a Filter text field.
 */
internal interface FilterTextComponent {
  var text: String

  val component: Component

  fun addDocumentListener(listener: DocumentListener)

  companion object {
    fun createComponent(
      project: Project,
      logcatPresenter: LogcatPresenter,
      filterParser: LogcatFilterParser,
      initialText: String,
    ): FilterTextComponent =
      if (StudioFlags.LOGCAT_V2_NAMED_FILTERS_ENABLE.get() && LogcatSettings.getInstance().namedFiltersEnabled) {
        NamedFilterComponent(project, logcatPresenter, filterParser, initialText)
      }
      else {
        FilterTextField(project, logcatPresenter, filterParser, initialText)
      }
  }
}
