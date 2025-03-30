// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.android.formatter

import com.intellij.application.options.CodeStyleAbstractConfigurable
import com.intellij.lang.Language
import com.intellij.lang.xml.XMLLanguage
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider
import com.intellij.psi.codeStyle.CustomCodeStyleSettings
import org.jetbrains.android.util.AndroidBundle.message

class AndroidXmlCodeStyleSettingsProvider : CodeStyleSettingsProvider() {

  override fun createConfigurable(baseSettings: CodeStyleSettings, modelSettings: CodeStyleSettings) =
    object : CodeStyleAbstractConfigurable(baseSettings, modelSettings, message("group.Internal.Android.text")) {
      override fun createPanel(originalSettings: CodeStyleSettings) = AndroidXmlCodeStylePanel(language, originalSettings)
    }

  override fun getLanguage(): Language = XMLLanguage.INSTANCE

  override fun hasSettingsPage() = false

  override fun createCustomSettings(settings: CodeStyleSettings): CustomCodeStyleSettings = AndroidXmlCodeStyleSettings(settings)

}