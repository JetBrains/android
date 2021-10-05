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
package com.android.tools.idea.editors

import com.intellij.application.options.editor.CheckboxDescriptor
import com.intellij.application.options.editor.checkBox
import com.intellij.openapi.options.UiDslUnnamedConfigurable
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.layout.*
import org.jetbrains.android.util.AndroidBundle

class AndroidEditorAppearanceConfigurable : UiDslUnnamedConfigurable.Simple() {

  private val settings = AndroidEditorAppearanceSettings.getInstance()

  private val checkboxDescriptor = CheckboxDescriptor(
    AndroidBundle.message("android.editor.settings.appearance.enable.flags.for.languages"),
    PropertyBinding({ settings.state.enableFlagsForLanguages }, { settings.state.enableFlagsForLanguages = it })
  )

  override fun Panel.createContent() {
    group(AndroidBundle.message("android.editor.settings.appearance.title")) {
      row { checkBox(checkboxDescriptor) }
    }
  }
}
