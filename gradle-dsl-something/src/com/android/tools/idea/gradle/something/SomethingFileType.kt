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
package com.android.tools.idea.gradle.something

import com.intellij.openapi.fileTypes.LanguageFileType
import icons.GradleIcons
import javax.swing.Icon

class SomethingFileType : LanguageFileType(SomethingLanguage.INSTANCE) {
  override fun getName(): String = "Something (Gradle Declarative)"
  override fun getDescription(): String = "Something (Gradle Declarative Build DSL)"
  override fun getDefaultExtension(): String = "something"
  override fun getIcon(): Icon = GradleIcons.GradleFile

  companion object {
    @JvmStatic
    val INSTANCE = SomethingFileType()
  }
}