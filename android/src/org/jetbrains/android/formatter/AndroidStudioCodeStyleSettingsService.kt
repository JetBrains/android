/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.intellij.psi.codeStyle.CodeStyleSettingsService
import com.intellij.psi.codeStyle.CodeStyleSettingsServiceImpl
import com.intellij.psi.codeStyle.CustomCodeStyleSettingsFactory

/**
 * This overrides [CodeStyleSettingsService] in the platform to adjust the order of code style
 * setting initialization. Specifically, it ensures [AndroidStudioCodeStyleSettingsProvider]
 * gets initialized _last_ so that it can successfully mutate the other code style settings.
 * Normally this would be accomplished by using order="last" in the extension registration
 * but [CodeStyleSettingsServiceImpl] unfortunately rearranges the extensions further.
 *
 * This is all tested by AndroidCodeStyleSettingsTest.
 */
class AndroidStudioCodeStyleSettingsService(
  private val delegate: CodeStyleSettingsService = CodeStyleSettingsServiceImpl(),
) : CodeStyleSettingsService by delegate {

  override fun getCustomCodeStyleSettingsFactories(): List<CustomCodeStyleSettingsFactory?> {
    val factories = delegate.customCodeStyleSettingsFactories
    val (ours, theirs) = factories.partition { f -> f is AndroidStudioCodeStyleSettingsProvider }
    return theirs + ours
  }
}