/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.nav.safeargs.kotlin.k2

import com.android.tools.idea.nav.safeargs.index.NavArgumentData
import com.android.tools.idea.nav.safeargs.psi.java.getPsiTypeStr
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

// getPsiTypeStr() returns Java type strings, so they need to be converted to their Kotlin
// equivalents.
private val TYPE_MAP =
  mapOf(
    "int" to "kotlin.Int",
    "int[]" to "kotlin.IntArray",
    "long" to "kotlin.Long",
    "long[]" to "kotlin.LongArray",
    "float" to "kotlin.Float",
    "float[]" to "kotlin.FloatArray",
    "java.lang.String" to "kotlin.String",
    "java.lang.String[]" to "kotlin.Array<kotlin.String>",
    "boolean" to "kotlin.Boolean",
    "boolean[]" to "kotlin.BooleanArray",
  )

fun NavArgumentData.resolveKotlinType(modulePackageName: String): String {
  val psiType = this.getPsiTypeStr(modulePackageName)
  val nonNullType =
    TYPE_MAP[psiType]
      ?: if (psiType.endsWith("[]")) {
        "kotlin.Array<${psiType.removeSuffix("[]").escapeFqNameComponents()}>"
      } else {
        psiType.escapeFqNameComponents()
      }

  return if (isNonNull()) {
    nonNullType
  } else {
    "$nonNullType?"
  }
}

val KEYWORDS_TO_ESCAPE: Set<String> =
  KtTokens.KEYWORDS.types.mapNotNullTo(mutableSetOf()) { (it as? KtKeywordToken)?.value }

fun String.escapeKeywords(): String = if (this in KEYWORDS_TO_ESCAPE) "`$this`" else this

fun String.escapeFqNameComponents(): String = split(".").joinToString(".") { it.escapeKeywords() }

fun String.escapeNewlinesForComment(): String = replace('\n', '⏎')

fun Name.toEscapedString(): String = asString().escapeKeywords()

fun FqName.toEscapedString(): String = pathSegments().joinToString(".") { it.toEscapedString() }

fun ClassId.toEscapedString(): String = asSingleFqName().toEscapedString()
