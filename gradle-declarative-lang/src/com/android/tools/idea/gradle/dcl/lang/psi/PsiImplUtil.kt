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

import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.BOOLEAN
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.DOUBLE_LITERAL
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.INTEGER_LITERAL
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.LONG_LITERAL
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.MULTILINE_STRING_LITERAL
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.NULL
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.ONE_LINE_STRING_LITERAL
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.UNSIGNED_INTEGER
import com.android.tools.idea.gradle.dcl.lang.parser.DeclarativeElementTypeHolder.UNSIGNED_LONG
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.childLeafs
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.util.elementType

class PsiImplUtil {
  companion object {
    @JvmStatic
    fun getReceiver(property: DeclarativeProperty): DeclarativeProperty? = when (property) {
      is DeclarativeBare -> null
      is DeclarativeQualified -> property.property
      else -> throw IllegalStateException("Unexpected DeclarativeProperty class of type ${property.javaClass.name} in getReceiver()")
    }

    @JvmStatic
    fun getField(property: DeclarativeProperty): DeclarativeIdentifier = when (property) {
      is DeclarativeBare -> property.identifier
      is DeclarativeQualified -> property.identifier
      else -> throw IllegalStateException("Unexpected DeclarativeProperty class of type ${property.javaClass.name} in getField()")
    }

    @JvmStatic
    fun getField(property: DeclarativePropertyReceiver): DeclarativeIdentifier = when (property) {
      is DeclarativeBareReceiver -> property.identifier
      is DeclarativeQualifiedReceiver -> property.identifier
      else -> throw IllegalStateException("Unexpected DeclarativeProperty class of type ${property.javaClass.name} in getField()")
    }

    @JvmStatic
    fun getReference(property: DeclarativeProperty): PsiReference? = getReferences(property).firstOrNull()

    @JvmStatic
    fun getReferences(property: DeclarativeProperty): Array<PsiReference> =
      ReferenceProvidersRegistry.getReferencesFromProviders(property)

    @JvmStatic
    fun getReceiver(property: DeclarativeAssignableProperty): DeclarativeAssignableProperty? = when (property) {
      is DeclarativeAssignableBare -> null
      is DeclarativeAssignableQualified -> property.assignableProperty
      else -> throw IllegalStateException("Unexpected DeclarativeProperty class of type ${property.javaClass.name} in getReceiver()")
    }

    @JvmStatic
    fun getReceiver(property: DeclarativeSimpleFactory): DeclarativeFactoryReceiver? = null

    @JvmStatic
    fun getReceiver(property: DeclarativeFactoryPropertyReceiver): DeclarativePropertyReceiver? =
      property.propertyReceiver

    @JvmStatic
    fun getField(property: DeclarativeAssignableProperty): DeclarativeIdentifier = when (property) {
      is DeclarativeAssignableBare -> property.identifier
      is DeclarativeAssignableQualified -> property.identifier
      else -> throw IllegalStateException("Unexpected DeclarativeProperty class of type ${property.javaClass.name} in getField()")
    }

    @JvmStatic
    fun getIdentifier(assignment: DeclarativeAssignment): DeclarativeIdentifier =
      assignment.assignableProperty.field

    @JvmStatic
    fun getAssignmentType(assignment: DeclarativeAssignment): AssignmentType =
      assignment.children.getOrNull(1)?.let{
         when (it.text){
          "=" -> AssignmentType.ASSIGNMENT
          "+=" -> AssignmentType.APPEND
           else -> throw IllegalStateException("Unknown assignment type: `${assignment.text}`")
        }
      } ?: throw IllegalStateException("Unknown assignment type: `${assignment.text}`")

    @JvmStatic
    fun getIdentifier(receiver: DeclarativeQualifiedReceiver): DeclarativeIdentifier =
       PsiTreeUtil.getChildOfType(receiver, DeclarativeIdentifier::class.java)!!

    @JvmStatic
    fun getIdentifier(receiver: DeclarativeReceiverPrefixedFactory): DeclarativeIdentifier =
      PsiTreeUtil.getChildOfType(receiver, DeclarativeIdentifier::class.java)!!

    @JvmStatic
    fun getReference(property: DeclarativeAssignableProperty): PsiReference? = getReferences(property).firstOrNull()

    @JvmStatic
    fun getReferences(property: DeclarativeAssignableProperty): Array<PsiReference> =
      ReferenceProvidersRegistry.getReferencesFromProviders(property)

    // Name should be nullable to agree with CompositePsiElement.getName() in PSI impl classes
    @JvmStatic
    fun getName(property: DeclarativeIdentifier): String? {
      var text = property.text
      if (text.startsWith("`") && text.endsWith("`"))
        text = text.drop(1).dropLast(1)
      return StringUtil.unescapeStringCharacters(text)
    }

    @JvmStatic
    fun getValue(assignment: DeclarativeAssignment): DeclarativeValue? {
      return assignment.children.firstNotNullOfOrNull { child -> (child as? DeclarativeValue).takeIf { it != null } }
    }

    @JvmStatic
    fun getFirst(pair: DeclarativePair): Any? {
      // need to get second DeclarativeValueElement as first is the key
      val text = pair.firstChild.text
      return when (pair.firstChild.elementType) {
        BOOLEAN -> text == "true"
        MULTILINE_STRING_LITERAL -> text.unTripleQuote().unescape()
        ONE_LINE_STRING_LITERAL -> text.unquote().unescape()
        LONG_LITERAL -> text?.toIntegerOrNull()
        DOUBLE_LITERAL -> text?.toDoubleOrNull()
        INTEGER_LITERAL -> text?.toIntegerOrNull()
        UNSIGNED_LONG -> text?.toIntegerOrNull()
        UNSIGNED_INTEGER -> text?.toIntegerOrNull()
        NULL -> null
        else -> null
      }
    }

    @JvmStatic
    fun getSecond(pair: DeclarativePair): DeclarativeValue {
      return pair.children.filterIsInstance<DeclarativeValue>().first()
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
    fun getIdentifier(block: DeclarativeBlock): DeclarativeIdentifier {
      return PsiTreeUtil.getChildOfType(block, DeclarativeIdentifier::class.java)
             ?: block.embeddedFactory?.identifier
             ?: throw IllegalStateException("DeclarativeBlock `${block.text}` does not have identifier")
    }

    @JvmStatic
    fun getBlockEntriesStart(blockGroup: DeclarativeBlockGroup): PsiElement? {
      return blockGroup.childLeafs().find { it.text == "{" }
    }

    @JvmStatic
    fun getArguments(list: DeclarativeArgumentsList): List<DeclarativeValue> {
      return list.children.flatMap { it.childrenOfType<DeclarativeValue>() }.toList()
    }

    @JvmStatic
    fun getValue(list: DeclarativeArgument): DeclarativeValue {
      return list.childrenOfType<DeclarativeValue>().first()
    }

    @JvmStatic
    fun getReceiver(receiver: DeclarativeReceiverPrefixedFactory): DeclarativeFactoryReceiver =
      receiver.factoryReceiver

    @JvmStatic
    fun getReceiver(receiver: DeclarativePropertyReceiver): DeclarativePropertyReceiver? =
      when (receiver) {
        is DeclarativeQualifiedReceiver -> receiver.propertyReceiver
        else -> null
      }

    @JvmStatic
    fun getValue(literal: DeclarativeLiteral): Any? = when {
      literal.pair !=null -> literal.pair!!.second
      literal.boolean != null -> literal.boolean?.text == "true"
      literal.multilineStringLiteral != null -> literal.multilineStringLiteral?.text?.unTripleQuote()?.unescape()
      literal.oneLineStringLiteral != null -> literal.oneLineStringLiteral?.text?.unquote()?.unescape()
      literal.longLiteral != null -> literal.longLiteral?.text?.toIntegerOrNull()
      literal.doubleLiteral != null -> literal.doubleLiteral?.text?.toDoubleOrNull()
      literal.integerLiteral != null -> literal.integerLiteral?.text?.toIntegerOrNull()
      literal.unsignedLong != null -> literal.unsignedLong?.text?.toIntegerOrNull()
      literal.unsignedInteger != null -> literal.unsignedInteger?.text?.toIntegerOrNull()
      literal.elementType == NULL -> null
      else -> null
    }

    private fun String.unquote() = this.removePrefix("\"").removeSuffixIfPresent("\"")
    private fun String.unTripleQuote() = this.removePrefix("\"\"\"").removeSuffixIfPresent("\"\"\"")
    private fun String.removeSuffixIfPresent(suffix: String) = if (this.endsWith(suffix)) this.dropLast(suffix.length) else this
    private fun String.toIntegerOrNull(): Any? {
      if (isEmpty()) return null
      var str = this.replace("_", "")
      val longIndicator = last().lowercaseChar() == 'l'
      if (longIndicator) {
        str = dropLast(1)
        return if (str.last().lowercaseChar() == 'u') str.dropLast(1).toULong()
        else str.toLongOrNull()
      }
      return if (str.last().lowercaseChar() == 'u') {
        when (val answer = str.dropLast(1).toULong()) {
          null -> null
          in UInt.MIN_VALUE..UInt.MAX_VALUE -> answer.toUInt()
          else -> answer
        }
      }
      else
        when (val answer = str.toLongOrNull()) {
          null -> null
          in Int.MIN_VALUE..Int.MAX_VALUE -> answer.toInt()
          else -> answer
        }
    }
  }
}