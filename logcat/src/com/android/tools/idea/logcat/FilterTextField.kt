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
package com.android.tools.idea.logcat

import com.android.tools.idea.logcat.filters.parser.LogcatFilterLanguage
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.ui.LanguageTextField
import org.jetbrains.annotations.VisibleForTesting

/**
 * A text field for the filter.
 *
 * TODO(aalbert): Add features like a `Clear Text` button (x), History etc.
 */
internal class FilterTextField(project: Project, private val logcatPresenter: LogcatPresenter, text: String)
  : LanguageTextField(LogcatFilterLanguage, project, text) {

  @VisibleForTesting
  public override fun createEditor(): EditorEx {
    return super.createEditor().apply {
      putUserData(TAGS_PROVIDER_KEY, logcatPresenter)
      putUserData(PACKAGE_NAMES_PROVIDER_KEY, logcatPresenter)
    }
  }
}
