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
package com.android.tools.idea.gradle.project.sync.idea.data.service

import com.intellij.openapi.roots.impl.LanguageLevelProjectExtensionImpl
import com.intellij.openapi.project.Project
import com.intellij.pom.java.LanguageLevel

// TODO (b/257937092) Remove this applied workaround to fix preview JDK version warning
class AndroidLanguageLevelProjectExtensionImpl(project: Project) : LanguageLevelProjectExtensionImpl(project) {

  override fun setLanguageLevel(languageLevel: LanguageLevel) {
    val currentLanguageLevel = when(languageLevel) {
      LanguageLevel.JDK_17_PREVIEW -> LanguageLevel.JDK_17
      LanguageLevel.JDK_18_PREVIEW -> LanguageLevel.JDK_18
      LanguageLevel.JDK_19_PREVIEW -> LanguageLevel.JDK_19
      else -> languageLevel
    }
    super.setLanguageLevel(currentLanguageLevel)
  }
}