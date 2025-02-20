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
package com.android.tools.idea.gradle.dcl.lang.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiParserFacade
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil

class DeclarativePsiFactory(private val project: Project) {
  fun createFile(text: CharSequence): DeclarativeFile =
    PsiFileFactory.getInstance(project)
      .createFileFromText(
        "placeholder.dcl",
        DeclarativeFileType.INSTANCE,
        text,
        System.currentTimeMillis(),
        false
      ) as DeclarativeFile

  private inline fun <reified T : DeclarativeElement> createFromText(code: String): T? =
    createFile(code).descendantOfType()

  private inline fun <reified T : PsiElement> PsiElement.descendantOfType(): T? =
    PsiTreeUtil.findChildOfType(this, T::class.java, false)

  fun createLiteralFromText(value: String): DeclarativeLiteral =
    createFromText("placeholder = $value") ?: error("Failed to create Declarative literal from text \"$value\"")

  fun createLiteral(value: Any?): DeclarativeLiteral =
    when (value) {
      is String -> if (value.contains('\n')) createMultiStringLiteral(value) else createStringLiteral(value)
      is Int -> createIntLiteral(value)
      is Long -> createLongLiteral(value)
      is Float -> createFloatLiteral(value)
      is Double -> createDoubleLiteral(value)
      is ULong -> createULongLiteral(value)
      is UInt -> createUIntLiteral(value)
      is Boolean -> createBooleanLiteral(value)
      else -> error("Failed to create Declarative literal with type ${value?.javaClass ?: "null"}")
    }

  fun createStringLiteral(value: String): DeclarativeLiteral =
    createFromText("placeholder = \"${value.escape()}\"") ?: error("Failed to create Declarative string from $value")

  fun createMultiStringLiteral(value: String): DeclarativeLiteral =
    createFromText("placeholder = \"\"\"${value.escapeMultilineString()}\"\"\"") ?: error("Failed to create Declarative string from $value")

  fun createIntLiteral(value: Int): DeclarativeLiteral =
    createFromText("placeholder = $value") ?: error("Failed to create Declarative Int from $value")

  fun createDoubleLiteral(value: Double): DeclarativeLiteral =
    createFromText("placeholder = $value") ?: error("Failed to create Declarative Double from $value")

  fun createFloatLiteral(value: Float): DeclarativeLiteral =
    createFromText("placeholder = $value") ?: error("Failed to create Declarative Double from $value")

  fun createLongLiteral(value: Long): DeclarativeLiteral {
    val text = when (value) {
      in Int.MIN_VALUE..Int.MAX_VALUE -> "${value}L"
      else -> "$value"
    }
    return createFromText("placeholder = $text") ?: error("Failed to create Declarative Long from $value")
  }

  fun createUIntLiteral(value: UInt): DeclarativeLiteral =
    createFromText("placeholder = ${value}U") ?: error("Failed to create Declarative Int from $value")

  fun createULongLiteral(value: ULong): DeclarativeLiteral {
    val text = when (value) {
      in UInt.MIN_VALUE..UInt.MAX_VALUE -> "${value}UL"
      else -> "${value}UL"
    }
    return createFromText("placeholder = $text") ?: error("Failed to create Declarative Long from $value")
  }

  fun createBooleanLiteral(value: Boolean): DeclarativeLiteral =
    createFromText("placeholder = $value") ?: error("Failed to create Declarative Boolean from $value")

  fun createNewline(): PsiElement = createToken("\n")

  fun createComma(): LeafPsiElement = createFile(",").descendantOfType()!!
  fun createDot(): LeafPsiElement = createFile(".").descendantOfType()!!

  private fun createToken(token: String): PsiElement =
    PsiParserFacade.getInstance(project).createWhiteSpaceFromText(token)

  fun createIdentifier(identifier: String): DeclarativeIdentifier {
    val id = identifier.maybeAddBackticks()
    return createFromText("$id()") ?: error("Failed to create Declarative Identifier with name $id")
  }

  fun createBlock(identifier: String): DeclarativeBlock {
    val id = identifier.maybeAddBackticks()
    return createFromText("$id {\n}") ?: error("Failed to create Declarative Block with name $id")
  }

  fun createAssignment(key: String, value: Any): DeclarativeAssignment {
    val id = key.maybeAddBackticks()
    return createFromText("$id = $value") ?: error("Failed to create DeclarativeAssignment `$id = $value`")
  }

  fun createAppendAssignment(key: String, value: Any): DeclarativeAssignment {
    val id = key.maybeAddBackticks()
    return createFromText("$id += $value") ?: error("Failed to create DeclarativeAssignment `$id = $value`")
  }

  fun createArgumentList(): DeclarativeArgumentsList {
    val list: DeclarativeArgumentsList = createFromText("function(placeholder)") ?: error("Failed to create DeclarativeArgumentsList")
    list.argumentList.clear()
    return list
  }

  private fun String.maybeAddBackticks(): String =
    if (this.matches("[a-zA-Z0-9_]+".toRegex())) this else "`$this`"

  fun createPrefixedFactory(): DeclarativeFactoryReceiver {
    val prefixedFactory: DeclarativeFactoryReceiver = createFromText("fun().fun()") ?: error(
      "Failed to create DeclarativeReceiverPrefixedFactory")
    prefixedFactory.children.forEach { it.delete() }
    return prefixedFactory
  }

  fun createArgument(value: DeclarativeValue, identifier: String? = null): DeclarativeArgument =
    (if (identifier == null)
      createFromText("function(${value.text})")
    else
      createFromText("function($identifier = ${value.text})"))
    ?: error("Failed to create DeclarativeArgument `$identifier = ${value.text}`")

  fun createProperty(value: String): DeclarativeProperty =
    createFromText("placeholder = $value") ?: error("Failed to create DeclarativeProperty `$value`")

  fun createFactory(identifier: String): DeclarativeSimpleFactory {
    val id = identifier.maybeAddBackticks()
    val factory = createFromText<DeclarativeSimpleFactory>("$id()")
    return factory ?: error("Failed to create createFactory `$id( )`")
  }

  fun createOneParameterFactoryBlock(identifier: String, parameter: String): DeclarativeBlock {
    val param = createLiteral(parameter).text
    val id = identifier.maybeAddBackticks()
    val factory = createFromText<DeclarativeBlock>("$id($param){ }")
    return factory ?: error("Failed to create createFactory `$id($param){}`")
  }

  fun createOneParameterFactory(identifier: String,
                                plainParameter: Any,
                                parameterIdentifier: String? = null): DeclarativeSimpleFactory {
    val id = identifier.maybeAddBackticks()
    val factory =
      if (parameterIdentifier == null)
        createFromText<DeclarativeSimpleFactory>("$id($plainParameter)")
      else
        createFromText<DeclarativeSimpleFactory>("$id($parameterIdentifier = $plainParameter)")

    return factory ?: error("Failed to create createFactory `$id($plainParameter)`")
  }
}
