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
package com.android.tools.idea.gradle.declarative.parser

import com.android.tools.idea.gradle.declarative.psi.DeclarativeArgumentsList
import com.android.tools.idea.gradle.declarative.psi.DeclarativeAssignment
import com.android.tools.idea.gradle.declarative.psi.DeclarativeBare
import com.android.tools.idea.gradle.declarative.psi.DeclarativeBlock
import com.android.tools.idea.gradle.declarative.psi.DeclarativeBlockGroup
import com.android.tools.idea.gradle.declarative.psi.DeclarativeEntry
import com.android.tools.idea.gradle.declarative.psi.DeclarativeProperty
import com.android.tools.idea.gradle.declarative.psi.DeclarativeFactory
import com.android.tools.idea.gradle.declarative.psi.DeclarativeIdentifier
import com.android.tools.idea.gradle.declarative.psi.DeclarativeLiteral
import com.android.tools.idea.gradle.declarative.psi.DeclarativeQualified
import com.android.tools.idea.gradle.declarative.psi.DeclarativeValue
import com.android.tools.idea.gradle.declarative.psi.unescape
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.psi.util.childLeafs
import com.intellij.psi.util.childrenOfType

class PsiImplUtil {
  companion object {
    @JvmStatic
    fun getReceiver(property: DeclarativeProperty): DeclarativeProperty? = when (property) {
      is DeclarativeBare -> null
      is DeclarativeQualified -> property.property
      else -> error("foo")
    }

    @JvmStatic
    fun getField(property: DeclarativeProperty): DeclarativeIdentifier = when (property) {
      is DeclarativeBare -> property.identifier
      is DeclarativeQualified -> property.identifier!!
      else -> error("foo")
    }

    @JvmStatic
    fun getReference(property: DeclarativeProperty): PsiReference? = getReferences(property).firstOrNull()

    @JvmStatic
    fun getReferences(property: DeclarativeProperty): Array<PsiReference> =
      ReferenceProvidersRegistry.getReferencesFromProviders(property)

    @JvmStatic
    fun getName(property: DeclarativeIdentifier): String? {
      return StringUtil.unescapeStringCharacters(property.text)
    }

    @JvmStatic
    fun getFactory(block: DeclarativeBlock): DeclarativeFactory? {
      return block.firstChild as? DeclarativeFactory
    }

    @JvmStatic
    fun getValue(assignment: DeclarativeAssignment): DeclarativeValue? {
      return assignment.children.firstNotNullOfOrNull { child -> (child as? DeclarativeValue).takeIf { it != null } }
    }

    @JvmStatic
    fun getBlockEntriesStart(block: DeclarativeBlock): PsiElement? {
      return block.childLeafs().find { it.text == "{" }
    }

    @JvmStatic
    fun getEntries(block: DeclarativeBlock): List<DeclarativeEntry> {
      return block.blockGroup.entries
    }


    @JvmStatic
    fun getBlockEntriesStart(blockGroup: DeclarativeBlockGroup): PsiElement? {
      return blockGroup.childLeafs().find { it.text == "{" }
    }

    @JvmStatic
    fun getArguments(list: DeclarativeArgumentsList): List<DeclarativeValue> {
      return list.childrenOfType<DeclarativeValue>().toList()
    }

    @JvmStatic
    fun getValue(literal: DeclarativeLiteral): Any? = when {
      literal.boolean != null -> literal.boolean?.text == "true"
      literal.string != null -> literal.string?.text?.unquote()?.unescape()
      literal.number != null -> literal.number?.text?.toIntegerOrNull()
      else -> null
    }

    private fun String.unquote() = this.removePrefix("\"").removeSuffixIfPresent("\"")
    private fun String.removeSuffixIfPresent(suffix: String) = if (this.endsWith(suffix)) this.dropLast(suffix.length) else this
    private fun String.toIntegerOrNull(): Any? {
      if (isEmpty()) return null
      val longIndicator = last().lowercaseChar() == 'l'
      if (longIndicator) return dropLast(1).replace("_", "").toLongOrNull()
      return when (val answer = replace("_", "").toLongOrNull()) {
        null -> null
        in Int.MIN_VALUE..Int.MAX_VALUE -> answer.toInt()
        else -> answer
      }

    }
  }
}