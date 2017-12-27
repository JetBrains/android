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
package com.android.tools.idea.templates

import freemarker.template.*

/**
 * Method invoked by FreeMarker to escape a literal or
 * package name into a valid Kotlin literal or package name
 */
class FmEscapeKotlinIdentifierMethod : TemplateMethodModelEx {
  @Throws(TemplateModelException::class)
  override fun exec(args: List<*>): TemplateModel {
    if (args.size != 1) {
      throw TemplateModelException("Wrong arguments")
    }

    val argument = (args[0] as TemplateScalarModel).asString
    val escaped = escape(argument)
    return SimpleScalar(escaped)
  }

  fun escape(s: String): String =
      if (s.contains(".")) {
        s.split(".").asSequence().joinToString(separator = ".") { escapeSingle(it) }
      }
      else {
        escapeSingle(s)
      }

  private fun escapeSingle(s: String): String =
      when {
        isKotlinKeyword(s) -> "`$s`"
        else -> s
      }

  private fun isKotlinKeyword(keyword: String): Boolean =
      // From https://github.com/JetBrains/kotlin/blob/master/core/descriptors/src/org/jetbrains/kotlin/renderer/KeywordStringsGenerated.java
      when (keyword) {
        "package", "as", "typealias", "class", "this", "super", "val", "var", "fun", "for", "null", "true", "false", "is", "in",
        "throw", "return", "break", "continue", "object", "if", "try", "else", "while", "do", "when", "interface", "typeof" -> true
        else -> false
      }
}