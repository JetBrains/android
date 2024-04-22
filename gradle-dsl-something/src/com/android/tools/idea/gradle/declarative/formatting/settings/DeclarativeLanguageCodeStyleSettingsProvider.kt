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
package com.android.tools.idea.gradle.declarative.formatting.settings

import com.android.tools.idea.gradle.declarative.DeclarativeLanguage
import com.intellij.application.options.CodeStyleAbstractConfigurable
import com.intellij.application.options.CodeStyleAbstractPanel
import com.intellij.application.options.IndentOptionsEditor
import com.intellij.application.options.SmartIndentOptionsEditor
import com.intellij.lang.Language
import com.intellij.psi.codeStyle.CodeStyleConfigurable
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CustomCodeStyleSettings
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider

class DeclarativeLanguageCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider() {
  override fun getLanguage(): Language = DeclarativeLanguage.INSTANCE

  override fun createCustomSettings(settings: CodeStyleSettings): CustomCodeStyleSettings =
    DeclarativeCodeStyleSettings(settings)

  override fun createConfigurable(
    baseSettings: CodeStyleSettings,
    modelSettings: CodeStyleSettings
  ): CodeStyleConfigurable {
    return object : CodeStyleAbstractConfigurable(baseSettings, modelSettings, configurableDisplayName) {
      override fun createPanel(settings: CodeStyleSettings): CodeStyleAbstractPanel =
        DeclarativeCodeStyleMainPanel(currentSettings, settings)
    }
  }

  override fun getCodeSample(settingsType: SettingsType): String =
    when (settingsType) {
      SettingsType.INDENT_SETTINGS -> INDENT_SAMPLE
      else -> ""
    }

  override fun getIndentOptionsEditor(): IndentOptionsEditor = SmartIndentOptionsEditor()
}

private fun sample(@org.intellij.lang.annotations.Language("Declarative") code: String) = code.trim()

private val INDENT_SAMPLE = sample("""
block {
  key = "value"
  anotherKey = factory("parameter")
}
""")
