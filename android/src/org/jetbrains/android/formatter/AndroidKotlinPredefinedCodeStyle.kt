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
package org.jetbrains.android.formatter

import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.PredefinedCodeStyle
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.core.formatter.KotlinCodeStyleSettings
import org.jetbrains.kotlin.idea.core.formatter.KotlinPackageEntryTable

/** Apply Android Kotlin code style */
class AndroidKotlinPredefinedCodeStyle : PredefinedCodeStyle("Android", KotlinLanguage.INSTANCE) {
  override fun apply(settings: CodeStyleSettings) {
    val kotlinSettings = settings.getCustomSettings(KotlinCodeStyleSettings::class.java)
    kotlinSettings.NAME_COUNT_TO_USE_STAR_IMPORT = Int.MAX_VALUE
    kotlinSettings.NAME_COUNT_TO_USE_STAR_IMPORT_FOR_MEMBERS = Int.MAX_VALUE
    kotlinSettings.PACKAGES_TO_USE_STAR_IMPORTS = KotlinPackageEntryTable()
  }
}
