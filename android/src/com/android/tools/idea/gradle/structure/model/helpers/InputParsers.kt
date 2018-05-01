/*
 * Copyright (C) 2017 The Android Open Source Project
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
@file:Suppress("UNUSED_PARAMETER")

package com.android.tools.idea.gradle.structure.model.helpers

import com.android.tools.idea.gradle.dsl.api.util.LanguageLevelUtil.parseFromGradleString
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.intellij.pom.java.LanguageLevel
import java.io.File

fun parseString(context: Any?, text: String): ParsedValue<String> =
    if (text == "")
      ParsedValue.NotSet
    else
      ParsedValue.Set.Parsed(value = text)

fun parseFile(context: Any?, text: String): ParsedValue<File> =
    if (text == "")
      ParsedValue.NotSet
    else
      ParsedValue.Set.Parsed(value = File(text))

inline fun <reified T> parseEnum(text: String, parser: (String) -> T?): ParsedValue<T> =
    if (text == "")
      ParsedValue.NotSet
    else {
      val parsed = parser(text)
      if (parsed != null)
        ParsedValue.Set.Parsed(value = parsed)
      else
        ParsedValue.Set.Invalid(text, "'${text}' is not a valid value of type ${T::class.simpleName}")
    }

fun parseBoolean(context: Any?, text: String): ParsedValue<Boolean> =
    when {
      text == "" -> ParsedValue.NotSet
      text.equals("true", ignoreCase = true) -> ParsedValue.Set.Parsed(value = true)
      text.equals("false", ignoreCase = true) -> ParsedValue.Set.Parsed(value = false)
      else -> ParsedValue.Set.Invalid(text, "Unknown boolean value: '$text'. Expected 'true' or 'false'")
    }

fun parseInt(context: Any?, text: String): ParsedValue<Int> =
    if (text == "")
      ParsedValue.NotSet
    else {
      try {
        ParsedValue.Set.Parsed(value = text.toInt())
      }
      catch (ex: NumberFormatException) {
        ParsedValue.Set.Invalid<Int>(dslText = text, errorMessage = "'$text' is not a valid integer value")
      }
    }

fun parseLanguageLevel(context: Any?, text: String): ParsedValue<LanguageLevel> =
  parseFromGradleString(text)?.let { ParsedValue.Set.Parsed(value = it) }
  ?: ParsedValue.Set.Invalid(dslText = text, errorMessage = "'$text' is not a valid language level")

fun formatLanguageLevel(context: Any?, value: LanguageLevel): String = value.toJavaVersion().toString()