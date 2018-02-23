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
package com.android.tools.idea.gradle.structure.model.meta

/**
 * An outcome of parsing of a text or DSL expression representation of type [T].
 *
 * The parsing outcome falls into one of the following cases:
 *   - there is nothing to parse. See: [NotSet]
 *   - there is a valid input which can be parsed as [T]. See: [Set.Parsed]
 *   - there is an input that can't be parsed a valid expression of type [T]. See: [Set.Invalid]
 */
sealed class ParsedValue<out T> {
  /**
   * The outcome of parsing of a text input representing the case "Nothing to parse".
   */
  class NotSet<out T> : ParsedValue<T>()

  /**
   * An outcome of parsing of a text input representing the cases where there is some text input to parse.
   */
  sealed class Set<out T> : ParsedValue<T>() {

    /**
     * An outcome of parsing of a valid text input representing a value of type [T].
     */
    class Parsed<out T>(
        /**
         * The parsed value.
         *
         * The null value is only valid if it is a result of expression evaluation.
         */
        val value: T?,

        /**
         * The text of the DSL expression (if not trivial)
         */
        val dslText: DslText? = null
    ) : Set<T>()

    /**
     * An outcome of parsing of a text input which is not a valid representation of type [T].
     */
    class Invalid<out T>(
        /**
         * The original text input which might be a DSL expression.
         */
        val dslText: String,

        /**
         * The error message describing the error in a way that can be displayed in the UI.
         */
        val errorMessage: String
    ) : Set<T>()
  }
}

/**
 * Defines kinds of DSL expressions supported in PSD.
 */
enum class DslMode {
  /**
   * An expression which explicitly represents a parsed value.
   */
  LITERAL,
  /**
   * An expression which is a direct reference to another property or variable.
   */
  REFERENCE,
  /**
   * An expression of type [String] containing other DSL expressions in a form of ${expression}.
   */
  INTERPOLATED_STRING,
  /**
   * Any other DSL expression which does not fall into any of the cases above.
   */
  OTHER_UNPARSED_DSL_TEXT
}

/**
 * The text of a DSL expression together with its intended or parsed usage mode.
 */
data class DslText(val mode: DslMode, val text: String?)

/**
 * Returns the default text representation of [ParsedValue] with any well-known values resolved into their full names according
 * to [wellKnownValues].
 */
fun <PropertyT> ParsedValue<PropertyT>.getText(wellKnownValues: Map<PropertyT, String>? = null) = when (this) {
  is ParsedValue.NotSet -> ""
  is ParsedValue.Set.Parsed -> {
    val dsl = dslText ?: DslText(DslMode.LITERAL, null)
    when (dsl.mode) {
      DslMode.LITERAL -> if (value == null) "" else (wellKnownValues?.get(value) ?: value.toString())
      DslMode.REFERENCE -> "\$${dsl.text}"
      DslMode.OTHER_UNPARSED_DSL_TEXT -> throw IllegalStateException()
      DslMode.INTERPOLATED_STRING -> "\"${dsl.text}\""
    }
  }
  // TODO(b/72088462) Decide on how to handle unparsed DSL text.
  is ParsedValue.Set.Invalid -> "\$\$${dslText}"
}


