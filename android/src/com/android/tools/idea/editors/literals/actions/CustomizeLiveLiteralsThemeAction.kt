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
package com.android.tools.idea.editors.literals.actions

import com.android.tools.idea.editors.literals.ui.LiveLiteralsHighlightColorConfigurable
import com.intellij.application.options.colors.ColorAndFontOptions
import com.intellij.application.options.colors.ColorSettingsUtil
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.keymap.KeyMapBundle
import com.intellij.openapi.options.colors.ColorSettingsPages
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil
import com.intellij.openapi.options.newEditor.SettingsDialogFactory
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.android.util.AndroidBundle.message

/**
 * Action that opens the theme customization dialog selecting the "Live Literals" page.
 */
internal class CustomizeLiveLiteralsThemeAction : AnAction(message("live.literals.customize.appearance.title")) {
  override fun actionPerformed(e: AnActionEvent) {
    ColorAndFontOptions.selectOrEditColor(e.dataContext, null, LiveLiteralsHighlightColorConfigurable::class.java)
  }
}