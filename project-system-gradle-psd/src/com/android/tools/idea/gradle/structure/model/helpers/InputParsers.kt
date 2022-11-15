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

import com.android.ide.common.gradle.Version
import com.android.sdklib.AndroidTargetHash
import com.android.tools.idea.gradle.dsl.api.util.LanguageLevelUtil.parseFromGradleString
import com.android.tools.idea.gradle.structure.model.meta.Annotated
import com.android.tools.idea.gradle.structure.model.meta.DslText
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.annotateWithError
import com.android.tools.idea.gradle.structure.model.meta.annotated
import com.intellij.pom.java.LanguageLevel
import java.io.File

fun parseAny(text: String): Annotated<ParsedValue<Any>> =
  if (text == "")
    ParsedValue.NotSet.annotated()
  else
    ParsedValue.Set.Parsed(text.toIntOrNull() ?: text.toBigDecimalOrNull() ?: text.toBooleanOrNull() ?: text,
                           DslText.Literal).annotated()

fun parseString(text: String): Annotated<ParsedValue<String>> =
  if (text == "")
    ParsedValue.NotSet.annotated()
  else
    ParsedValue.Set.Parsed(text, DslText.Literal).annotated()

fun parseFile(text: String): Annotated<ParsedValue<File>> =
  if (text == "")
    ParsedValue.NotSet.annotated()
  else
    ParsedValue.Set.Parsed(File(text), DslText.Literal).annotated()

inline fun <reified T : Any> parseEnum(text: String, parser: (String) -> T?): Annotated<ParsedValue<T>> =
  if (text == "")
    ParsedValue.NotSet.annotated()
  else {
    val parsed = parser(text)
    if (parsed != null)
      ParsedValue.Set.Parsed(parsed, DslText.Literal).annotated()
    else
      ParsedValue.Set.Parsed(null, DslText.OtherUnparsedDslText(text))
        .annotateWithError("'${text}' is not a valid value of type ${T::class.simpleName}")
  }

private fun String.toBooleanOrNull() = when {
  this.equals("true", ignoreCase = true) -> true
  this.equals("false", ignoreCase = true) -> false
  else -> null
}

fun parseBoolean(text: String): Annotated<ParsedValue<Boolean>> =
  if (text == "")
    ParsedValue.NotSet.annotated()
  else {
    val parsed = text.toBooleanOrNull()
    if (parsed != null)
      ParsedValue.Set.Parsed(parsed, DslText.Literal).annotated()
    else
      ParsedValue.Set.Parsed(null, DslText.OtherUnparsedDslText(text))
        .annotateWithError("Unknown boolean value: '$text'. Expected 'true' or 'false'")
  }

fun parseInt(text: String): Annotated<ParsedValue<Int>> =
  if (text == "")
    ParsedValue.NotSet.annotated()
  else {
    try {
      ParsedValue.Set.Parsed(text.toInt(), DslText.Literal).annotated()
    }
    catch (ex: NumberFormatException) {
      ParsedValue.Set.Parsed<Int>(null, DslText.OtherUnparsedDslText(text))
        .annotateWithError("'$text' is not a valid integer value")
    }
  }

fun parseLanguageLevel(text: String): Annotated<ParsedValue<LanguageLevel>> =
  if (text == "")
    ParsedValue.NotSet.annotated()
  else
    parseFromGradleString(text)?.let { ParsedValue.Set.Parsed(it, DslText.Literal).annotated() }
    ?: ParsedValue.Set.Parsed(null, DslText.OtherUnparsedDslText(text))
      .annotateWithError("'$text' is not a valid language level")

fun parseHashString(text: String) =
  if (text.isEmpty()) ParsedValue.NotSet.annotated()
  else ParsedValue.Set.Parsed(text, DslText.Literal).annotated()


fun parseGradleVersion(text: String): Annotated<ParsedValue<Version>> =
  if (text == "")
    ParsedValue.NotSet.annotated()
  else
    try {
      ParsedValue.Set.Parsed(Version.parse(text), DslText.Literal).annotated()
    }
    catch (ex: IllegalArgumentException) {
      ParsedValue.Set.Parsed(null, DslText.OtherUnparsedDslText(text))
        .annotateWithError("'$text' is not a valid version specification")
    }

fun parseReferenceOnly(text: String): Annotated<ParsedValue<Unit>> =
  if (text == "")
    ParsedValue.NotSet.annotated()
  else
    ParsedValue.Set.Parsed(null, DslText.OtherUnparsedDslText(text))
      .annotateWithError("A signing config reference should be in a form of '\$configName'")

fun formatLanguageLevel(value: LanguageLevel): String = value.toJavaVersion().toString()

fun formatAny(value: Any): String {
  if (value !is String) return value.toString()
  val text = value.toString()
  return if ((text.toIntOrNull() ?: text.toBigDecimalOrNull() ?: text.toBooleanOrNull()) != null)
    "\"$text\""
  else text
}

fun formatUnit(value: Unit): String = ""

fun matchHashStrings(mode: Any?, parsed: String?, resolved: String) =
  AndroidTargetHash.getPlatformVersion(parsed.orEmpty())?.featureLevel == AndroidTargetHash.getPlatformVersion(resolved)?.featureLevel

fun matchFiles(rootDir: File?, parsed: File?, resolved: File): Boolean =
  parsed?.let { rootDir?.resolve(parsed) } == resolved

fun String.toIntOrString(): Any = this.toIntOrNull() ?: this