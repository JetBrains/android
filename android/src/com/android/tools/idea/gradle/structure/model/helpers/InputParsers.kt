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

import com.android.ide.common.repository.GradleVersion
import com.android.sdklib.AndroidTargetHash
import com.android.tools.idea.gradle.dsl.api.util.LanguageLevelUtil.parseFromGradleString
import com.android.tools.idea.gradle.structure.model.meta.*
import com.intellij.pom.java.LanguageLevel
import java.io.File

fun parseString(context: Any?, text: String): Annotated<ParsedValue<String>> =
  if (text == "")
    ParsedValue.NotSet.annotated()
  else
    ParsedValue.Set.Parsed(text, DslText.Literal).annotated()

fun parseFile(context: Any?, text: String): Annotated<ParsedValue<File>> =
  if (text == "")
    ParsedValue.NotSet.annotated()
  else
    ParsedValue.Set.Parsed(File(text), DslText.Literal).annotated()

inline fun <reified T> parseEnum(text: String, parser: (String) -> T?): Annotated<ParsedValue<T>> =
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

fun parseBoolean(context: Any?, text: String): Annotated<ParsedValue<Boolean>> =
  when {
    text == "" -> ParsedValue.NotSet.annotated()
    text.equals("true", ignoreCase = true) -> ParsedValue.Set.Parsed(true, DslText.Literal).annotated()
    text.equals("false", ignoreCase = true) -> ParsedValue.Set.Parsed(false, DslText.Literal).annotated()
    else ->
      ParsedValue.Set.Parsed(null, DslText.OtherUnparsedDslText(text))
        .annotateWithError("Unknown boolean value: '$text'. Expected 'true' or 'false'")
  }

fun parseInt(context: Any?, text: String): Annotated<ParsedValue<Int>> =
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

fun parseLanguageLevel(context: Any?, text: String): Annotated<ParsedValue<LanguageLevel>> =
  if (text == "")
    ParsedValue.NotSet.annotated()
  else
    parseFromGradleString(text)?.let { ParsedValue.Set.Parsed(it, DslText.Literal).annotated() }
    ?: ParsedValue.Set.Parsed(null, DslText.OtherUnparsedDslText(text))
      .annotateWithError("'$text' is not a valid language level")

fun parseHashString(context: Any?, text: String) =
  if (text.isEmpty()) ParsedValue.NotSet.annotated()
  else AndroidTargetHash.getPlatformVersion(text)?.let { ParsedValue.Set.Parsed(text, DslText.Literal).annotated() }
       ?: ParsedValue.Set.Parsed(text, DslText.Literal).annotateWithError("Invalid hash string")

fun parseGradleVersion(context: Any?, text: String): Annotated<ParsedValue<GradleVersion>> =
  if (text == "")
    ParsedValue.NotSet.annotated()
  else
    try {
      ParsedValue.Set.Parsed(GradleVersion.parse(text), DslText.Literal).annotated()
    }
    catch (ex: IllegalArgumentException) {
      ParsedValue.Set.Parsed(null, DslText.OtherUnparsedDslText(text))
        .annotateWithError("'$text' is not a valid version specification")
    }

fun parseReferenceOnly(context: Any?, text: String): Annotated<ParsedValue<Unit>> =
  if (text == "")
    ParsedValue.NotSet.annotated()
  else
    ParsedValue.Set.Parsed(null, DslText.OtherUnparsedDslText(text))
      .annotateWithError("A signing config reference should be in a form of '\$configName'")

fun formatLanguageLevel(context: Any?, value: LanguageLevel): String = value.toJavaVersion().toString()

fun formatUnit(context: Any?, value: Unit): String = ""

fun matchHashStrings(context: Any?, parsed: String?, resolved: String) =
  AndroidTargetHash.getPlatformVersion(parsed.orEmpty())?.featureLevel == AndroidTargetHash.getPlatformVersion(resolved)?.featureLevel

fun matchFiles(rootDir: File?, parsed: File?, resolved: File): Boolean =
  parsed?.let { rootDir?.resolve(parsed) } == resolved
