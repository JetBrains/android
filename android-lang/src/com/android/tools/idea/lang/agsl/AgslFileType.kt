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
package com.android.tools.idea.lang.agsl

import com.intellij.openapi.fileTypes.LanguageFileType
import icons.StudioIcons
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

/**
 * File type registration for AGSL. We don't actually intend to support developing AGSL as separate
 * source files, with their own dedicated source set in Gradle etc. However, IntelliJ appears to
 * bind syntax highlighting to file types. If we don't register this file type, then the nested
 * syntax highlighting for AGSL will **not** call [AgslSyntaxHighlighter.getTokenHighlights], and
 * many of the keywords are not highlighted.
 */
class AgslFileType private constructor() : LanguageFileType(AgslLanguage.INSTANCE) {
  @NonNls
  override fun getDefaultExtension(): String {
    return DEFAULT_ASSOCIATED_EXTENSION
  }

  override fun getDescription(): String {
    return AgslLanguage.INSTANCE.displayName
  }

  override fun getIcon(): Icon? {
    return StudioIcons.Common.ANDROID_HEAD
  }

  @NonNls
  override fun getName(): String {
    return AgslLanguage.ID
  }

  companion object {
    val INSTANCE: LanguageFileType = AgslFileType()

    @NonNls val DEFAULT_ASSOCIATED_EXTENSION = ".agsl"
  }
}
