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

import com.android.tools.idea.gradle.dcl.lang.ide.DeclarativeAnnotator.ElementTypeWithAugmentation
import com.android.tools.idea.gradle.dcl.lang.ide.ElementType.BLOCK
import com.android.tools.idea.gradle.dcl.lang.ide.ElementType.BOOLEAN
import com.android.tools.idea.gradle.dcl.lang.ide.ElementType.ENUM
import com.android.tools.idea.gradle.dcl.lang.ide.ElementType.FACTORY
import com.android.tools.idea.gradle.dcl.lang.ide.ElementType.FACTORY_BLOCK
import com.android.tools.idea.gradle.dcl.lang.ide.ElementType.INTEGER
import com.android.tools.idea.gradle.dcl.lang.ide.ElementType.LONG
import com.android.tools.idea.gradle.dcl.lang.ide.ElementType.PROPERTY
import com.android.tools.idea.gradle.dcl.lang.ide.ElementType.STRING
import com.android.tools.idea.gradle.dcl.lang.psi.AssignmentType
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeAbstractFactory
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeAssignment
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeBare
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeBlock
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeLiteral
import com.android.tools.idea.gradle.dcl.lang.sync.AugmentationKind
import com.android.tools.idea.gradle.dcl.lang.sync.BlockFunction
import com.android.tools.idea.gradle.dcl.lang.sync.ClassModel
import com.android.tools.idea.gradle.dcl.lang.sync.ClassType
import com.android.tools.idea.gradle.dcl.lang.sync.DataClassRef
import com.android.tools.idea.gradle.dcl.lang.sync.DataClassRefWithTypes
import com.android.tools.idea.gradle.dcl.lang.sync.DataProperty
import com.android.tools.idea.gradle.dcl.lang.sync.DataTypeReference
import com.android.tools.idea.gradle.dcl.lang.sync.EnumModel
import com.android.tools.idea.gradle.dcl.lang.sync.FullName
import com.android.tools.idea.gradle.dcl.lang.sync.GenericTypeRef
import com.android.tools.idea.gradle.dcl.lang.sync.ParameterizedClassModel
import com.android.tools.idea.gradle.dcl.lang.sync.PlainFunction
import com.android.tools.idea.gradle.dcl.lang.sync.SchemaFunction
import com.android.tools.idea.gradle.dcl.lang.sync.SchemaMemberFunction
import com.android.tools.idea.gradle.dcl.lang.sync.SimpleDataType
import com.android.tools.idea.gradle.dcl.lang.sync.SimpleTypeRef
import com.intellij.psi.PsiElement

enum class ElementType(val str: String) {
  STRING("String"),
  INTEGER("Integer"),
  LONG("Long"),
  BOOLEAN("Boolean"),
  ENUM("Enum"),
  BLOCK("Block element"),
  FACTORY_BLOCK("Factory block"),
  FACTORY("Factory"),
  PROPERTY("Property"),
  ENUM_CONSTANT("Enum Constant"),
  GENERIC_TYPE("Generic Type"),
  EXPRESSION("Expression")
}

fun getType(type: DataTypeReference, resolve: (FullName) -> ClassType?): ElementType = when (type) {
  is DataClassRef -> getDataClassType(resolve, type.fqName)
  is SimpleTypeRef -> getSimpleType(type.dataType)
  is DataClassRefWithTypes ->  getDataClassType(resolve, type.fqName)
  is GenericTypeRef -> ElementType.GENERIC_TYPE
}

private fun getDataClassType(
  resolve: (FullName) -> ClassType?,
  fullName: FullName,
) = when (val resolvedType = resolve(fullName)) {
    is ClassModel -> ElementType.PROPERTY

    is EnumModel -> ENUM
    // this is Gradle external class like Array or List
    is ParameterizedClassModel -> ElementType.PROPERTY
    else -> BLOCK
  }

fun getType(type: SchemaMemberFunction): ElementType = when (type.semantic) {
  is PlainFunction -> FACTORY
  is BlockFunction -> if (type.parameters.isNotEmpty()) FACTORY_BLOCK else BLOCK
}

val VALUE_CLASSES = setOf("org.gradle.api.file.RegularFile", "org.gradle.api.file.Directory")

fun ClassModel.isObjectValue(rootFunction: List<PlainFunction>) =
  rootFunction
    .map { it.returnValue }
    .filterIsInstance<DataClassRef>()
    .any { it.fqName == name } || name.name in VALUE_CLASSES

fun getType(type: SchemaFunction): ElementType = when (type.semantic) {
  is PlainFunction -> FACTORY
  is BlockFunction -> if (type.parameters.isNotEmpty()) FACTORY_BLOCK else BLOCK
}

fun getType(entry: EntryWithContext): ElementType = when (entry.entry) {
  is SchemaMemberFunction -> getType(entry.entry)
  is SchemaFunction -> getType(entry.entry)
  is DataProperty -> getType(entry.entry.valueType, entry::resolveRef)
}

fun getEnumConstants(entryWithContext: EntryWithContext?): List<String> {
  val entry = entryWithContext?.entry
  if (entry is DataProperty && entry.valueType is DataClassRef) {
    val enumEntity = entryWithContext.resolveRef((entry.valueType as DataClassRef).fqName)
    if (enumEntity is EnumModel)
      return enumEntity.entryNames
  }
  return listOf()
}

fun getSimpleType(type: SimpleDataType): ElementType = when (type) {
  SimpleDataType.INT -> INTEGER
  SimpleDataType.LONG -> LONG
  SimpleDataType.STRING -> STRING
  SimpleDataType.BOOLEAN -> BOOLEAN
  else -> PROPERTY
}

fun PsiElement.getElementType(): ElementTypeWithAugmentation? = when (this) {
  is DeclarativeBlock -> if (embeddedFactory != null) ElementTypeWithAugmentation(FACTORY_BLOCK, listOf()) else ElementTypeWithAugmentation(BLOCK, listOf())
  is DeclarativeAbstractFactory -> ElementTypeWithAugmentation(FACTORY, listOf())
  is DeclarativeAssignment -> {
    val augmented = if (assignmentType == AssignmentType.APPEND) listOf(AugmentationKind.PLUS) else listOf()
    when (val rvalue = value) {
      is DeclarativeBare -> ElementTypeWithAugmentation(ENUM, augmented)
      is DeclarativeLiteral ->
        when (rvalue.value) {
          is String -> ElementTypeWithAugmentation(STRING, augmented)
          is Int -> ElementTypeWithAugmentation(INTEGER, augmented)
          is Boolean -> ElementTypeWithAugmentation(BOOLEAN, augmented)
          is Long -> ElementTypeWithAugmentation(LONG, augmented)
          else -> ElementTypeWithAugmentation(ENUM, augmented)
        }

      is DeclarativeAbstractFactory -> ElementTypeWithAugmentation(PROPERTY, augmented)
      else -> null
    }
  }

  else -> null
}

// getting all service function like `uri(string)`
fun getRootFunctions(parent: PsiElement, schemas: BuildDeclarativeSchemas): List<SchemaFunction> =
  schemas.getTopLevelEntries(parent.containingFile.name)
    .map { it.entry }
    .filterIsInstance<SchemaFunction>()
    .filter { function ->
      when (val semantic = function.semantic) {
        is PlainFunction -> semantic.returnValue != SimpleTypeRef(SimpleDataType.UNIT)
        else -> false
      }
    }.distinct()

fun getRootProperties(parent: PsiElement, schemas: BuildDeclarativeSchemas): List<DataProperty> =
  schemas.getTopLevelEntries(parent.containingFile.name)
    .map { it.entry }
    .filterIsInstance<DataProperty>().distinct()

fun getRootPlainFunctions(parent: PsiElement, schemas: BuildDeclarativeSchemas): List<PlainFunction> =
  getRootFunctions(parent, schemas).map { it.semantic }.filterIsInstance<PlainFunction>()
