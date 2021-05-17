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
package com.android.tools.idea.stats

import com.android.tools.idea.serverflags.protos.Option
import com.android.tools.idea.serverflags.protos.Survey
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.IconLoader
import icons.StudioIcons
import javax.swing.Icon

val Option.icon: Icon?
  get() {
    val path = iconPath
    if (path.isNullOrBlank()) {
      return null
    }
    return IconLoader.getIcon(path, StudioIcons::class.java)
  }

fun createDialog(survey: Survey, choiceLogger: ChoiceLogger = ChoiceLoggerImpl, hasFollowup: Boolean = false): DialogWrapper {
  return if (survey.answerCount > 1) {
    MultipleChoiceDialog(survey, choiceLogger, hasFollowup)
  }
  else {
    SingleChoiceDialog(survey, choiceLogger, hasFollowup)
  }
}