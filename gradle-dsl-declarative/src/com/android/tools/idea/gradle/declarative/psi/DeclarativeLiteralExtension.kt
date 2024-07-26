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
package com.android.tools.idea.gradle.declarative.psi

import com.android.tools.idea.gradle.declarative.parser.DeclarativeElementTypeHolder
import com.intellij.lang.ASTNode

val DeclarativeLiteral.kind: DeclarativeLiteralKind?
  get() {
    val child = node.firstChildNode ?: return null
    return DeclarativeLiteralKind.fromAstNode(child) ?: error("Unknown literal: $child (`$text`)")
  }

sealed class DeclarativeLiteralKind(val node: ASTNode) {
  abstract val value: Any?

  class Boolean(node: ASTNode) : DeclarativeLiteralKind(node) {
    override val value: kotlin.Boolean = node.text == "true"
  }

  class Number(node: ASTNode) : DeclarativeLiteralKind(node) {
    override val value: kotlin.Number? = node.text.toIntOrNull(10)
  }

  class String(node: ASTNode) : DeclarativeLiteralKind(node) {
    override val value: kotlin.String = node.text.trim('\"')
  }

  companion object {
    fun fromAstNode(node: ASTNode): DeclarativeLiteralKind? {
      return when (node.elementType) {
        DeclarativeElementTypeHolder.BOOLEAN -> Boolean(node)
        DeclarativeElementTypeHolder.NUMBER -> Number(node)
        DeclarativeElementTypeHolder.STRING -> String(node)
        else -> null
      }
    }
  }
}