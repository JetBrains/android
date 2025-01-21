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
package com.android.tools.idea.gradle.dcl.lang.ide

import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeArgumentsList
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeAssignableProperty
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeBlock
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeElement
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeEntry
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeFactory
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeFactoryReceiver
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeIdentifier
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeIdentifierOwner
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeProperty
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeReceiverPrefixed
import com.android.tools.idea.gradle.dcl.lang.sync.BlockFunction
import com.android.tools.idea.gradle.dcl.lang.sync.ClassModel
import com.android.tools.idea.gradle.dcl.lang.sync.DataClassRef
import com.android.tools.idea.gradle.dcl.lang.sync.DataProperty
import com.android.tools.idea.gradle.dcl.lang.sync.EnumModel
import com.android.tools.idea.gradle.dcl.lang.sync.PlainFunction
import com.android.tools.idea.gradle.dcl.lang.sync.SchemaFunction
import com.android.tools.idea.gradle.dcl.lang.sync.SimpleDataType
import com.android.tools.idea.gradle.dcl.lang.sync.SimpleTypeRef
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement

class DeclarativeAnnotator : Annotator {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (!DeclarativeIdeSupport.isEnabled()) return
    if (element !is DeclarativeElement) return

    fun getSchema(): BuildDeclarativeSchemas? {
      val schema = DeclarativeService.getInstance(element.project).getDeclarativeSchema() ?: return null
      return schema
    }

    // check element naming
    if (element is DeclarativeIdentifier && element.parent !is DeclarativeProperty) {
      val schema = getSchema() ?: return
      val path = getPath(element)
      val result = mutableListOf<SearchResult>()
      result.addAll(searchForType(path, schema, element.containingFile.name))
      if (element.parent is DeclarativeFactoryReceiver) {
        // search for plain function like uri, file etc
        element.name?.let {
          result.addAll(searchForType(listOf(it), schema, element.containingFile.name))
          // also check direct parent if it has function like dependenciesDcl.project
          findParentBlock(element)?.let { parentBlock ->
            result.addAll(searchForType(getPath(parentBlock.identifier)+it, schema, element.containingFile.name))
          }
        }
      }
      if (result.isEmpty()) {
        showUnknownName(holder)
      }
    }
    // check element type
    // checking the whole element - assignment, function, block
    // does not check embedded elements
    if (element.isMainLevelElement()) {
      val identifier = element.findIdentifier() ?: return
      val path = getPath(identifier)
      val schema = getSchema() ?: return
      val result = searchForType(path, schema, element.containingFile.name)
      if (result.isEmpty()) return
      val types = result.map { it.toElementType() }.distinct()
      val foundType = types.any { it == element.getElementType() }
      if (!foundType) {
        showWrongType(types.map { it.str }, holder)
      }
    }
  }

  // psi element that has identifier we can build path to the root element
  private fun PsiElement.isMainLevelElement() =
    this is DeclarativeEntry

  private fun PsiElement.findIdentifier() =
    when (this) {
      is DeclarativeIdentifierOwner -> identifier
      else -> null
    }

  private fun findParentBlock(psi: PsiElement):DeclarativeBlock?{
    var current = psi

    while (current.parent!=null && current.parent !is DeclarativeBlock) {
      current = current.parent
    }
    return current.parent as? DeclarativeBlock
  }

  sealed class SearchResult {
    // TODO make it dynamic in future by requesting this from schema
    private var producerFunctionAvailable = listOf("java.net.URI")
    fun toElementType(): ElementType {
      return when (this) {
        is FoundFunction -> when (type.semantic) {
          is PlainFunction -> ElementType.FACTORY
          is BlockFunction -> if (type.parameters.isNotEmpty()) ElementType.FACTORY_BLOCK else ElementType.BLOCK
        }
        is FoundClass ->
          if (producerFunctionAvailable.contains(type.name.name))
            ElementType.FACTORY_VALUE
          else
            ElementType.BLOCK
        is FoundProperty -> getSimpleType(type)
        is FoundEnum -> ElementType.ENUM
      }
    }
  }

  data class FoundClass(val type: ClassModel) : SearchResult()
  data class FoundEnum(val type: EnumModel) : SearchResult()
  data class FoundFunction(val type: SchemaFunction) : SearchResult()
  data class FoundProperty(val type: SimpleDataType) : SearchResult()

  private fun searchForType(path: List<String>, schema: BuildDeclarativeSchemas, fileName: String): List<SearchResult> {
    if (path.isEmpty()) return listOf()

    var receivers: List<EntryWithContext> = schema.getTopLevelEntriesByName(path[0], fileName)
    val last = path.size - 1
    for (index in 1..last) {
      if (receivers.isEmpty()) {
        return listOf()
      }

      receivers = receivers.flatMap { it.getNextLevel(path[index]) }
    }

    return receivers.flatMap { receiver ->
      when (val entry = receiver.entry) {
        is SchemaFunction -> listOf(FoundFunction(entry))
        is DataProperty -> when (val type = entry.valueType) {
          is DataClassRef -> receiver.resolveRef(type.fqName)?.let {
            listOf(
              when (it) {
                is ClassModel -> FoundClass(it)
                is EnumModel -> FoundEnum(it)
              }
            )
          } ?: listOf()
          is SimpleTypeRef -> listOf(FoundProperty(type.dataType))
        }
      }
    }
  }

  private fun showUnknownName(holder: AnnotationHolder) {
    holder.newAnnotation(HighlightSeverity.ERROR, "Unknown identifier").create()
  }

  private fun showWrongType(types: List<String>, holder: AnnotationHolder) {
    if (types.size == 1)
      holder.newAnnotation(HighlightSeverity.ERROR,
                           "Element type should be of type: ${types.first()}").create()
    else
      holder.newAnnotation(HighlightSeverity.ERROR,
                           "Element type should be of one of types: ${types.joinToString(", ")}").create()
  }

  private fun getPath(element: DeclarativeIdentifier): List<String> {
    val result = mutableListOf<String>()
    var current: PsiElement = element
    while (current.parent != null && current is DeclarativeElement) {
      when (current) {
        is DeclarativeArgumentsList -> current = skip<DeclarativeFactory>(current)
        is DeclarativeAssignableProperty -> current = parseReceiver<DeclarativeAssignableProperty>(current, result).parent
        is DeclarativeFactoryReceiver ->
          current = parseReceiver<DeclarativeFactoryReceiver>(current, result)
        is DeclarativeFactory ->
          current = current.parent
        else -> {
          (current as? DeclarativeIdentifierOwner)?.identifier?.name?.let { result.add(it) }
          current = current.parent
        }
      }
    }
    return result.reversed()
  }

  private inline fun <reified T:DeclarativeElement> skip(current: PsiElement): PsiElement {
    var element: PsiElement = current
    while (element.parent != null && element.parent is T) {
      element = element.parent
    }
    return element.parent
  }

  private inline fun <reified T: DeclarativeReceiverPrefixed<T>> parseReceiver(property: DeclarativeReceiverPrefixed<T>, list: MutableList<String>): PsiElement {
    var currentProperty = property
    currentProperty.identifier.name?.let { list.add(it) }

    while (currentProperty.getReceiver() != null) {
      currentProperty = currentProperty.getReceiver()!!
      currentProperty.identifier.name?.let { list.add(it) }
    }
    return skip<T>(currentProperty)
  }
}