/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.lang.proguardR8

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

class ProguardR8FileType private constructor() : LanguageFileType(ProguardR8Language.INSTANCE) {

  override fun getName(): String {
    return "Shrinker Config File"
  }

  override fun getDescription(): String {
    return "Shrinker Config"
  }

  override fun getDefaultExtension(): String {
    return EXT_PRO
  }

  override fun getIcon(): Icon? {
    return AllIcons.FileTypes.Text
  }

  companion object {
    @JvmField
    val INSTANCE = ProguardR8FileType()

    @JvmField
    val EXT_PRO = "pro"

    @JvmField
    val DOT_PRO = ".pro"
  }
}
