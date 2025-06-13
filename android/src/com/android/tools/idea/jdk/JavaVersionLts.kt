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
package com.android.tools.idea.jdk

import com.intellij.util.lang.JavaVersion
import org.jetbrains.jps.model.java.LanguageLevel

enum class JavaVersionLts(val languageLevel: LanguageLevel) {
  JDK_1_8(LanguageLevel.JDK_1_8),
  JDK_11(LanguageLevel.JDK_11),
  JDK_17(LanguageLevel.JDK_17),
  JDK_21(LanguageLevel.JDK_21);

  companion object {
    fun isLtsVersion(javaVersion: JavaVersion): Boolean {
      return JavaVersionLts.entries.firstOrNull { it.languageLevel.toJavaVersion() == javaVersion } != null
    }
  }
}
