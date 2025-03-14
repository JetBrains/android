/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.dcl.lang.sync
import com.google.common.base.Objects
import java.io.Serializable

data class FullName(val name: String) : Serializable

// named entry that user can add to file - property, factory, block
sealed class Entry(val simpleName: String) : Serializable {
  fun getNextLevel(schema: BuildDeclarativeSchema, name: String): List<Entry> = getNextLevel(schema) { elementName -> elementName == name }
  fun getNextLevel(schema: BuildDeclarativeSchema): List<Entry> = getNextLevel(schema) { true }
  internal abstract fun getNextLevel(schema: BuildDeclarativeSchema, predicate: (name: String) -> Boolean): List<Entry>
}

sealed interface ClassType: Serializable {
  val name: FullName
}

data class ClassModel(
  val memberFunctions: List<SchemaMemberFunction>,
  override val name: FullName,
  val properties: List<DataProperty>,
  val supertypes: Set<FullName>,
) : ClassType

data class EnumModel(
  override val name: FullName,
  val entryNames: List<String>,
) : ClassType

sealed class FunctionSemantic : Serializable
data class PlainFunction(val returnValue: DataTypeReference) : FunctionSemantic()
data class BlockFunction(val accessor: DataClassRef) : FunctionSemantic()

data class SchemaMemberFunction(val receiver: DataClassRef,
                                override val name: String,
                                override val parameters: List<IdeDataParameter>,
                                override val semantic: FunctionSemantic) : SchemaFunction(name = name, parameters = parameters,
                                                                                          semantic = semantic) {
  override fun hashCode(): Int {
    return Objects.hashCode(name, receiver, parameters, semantic)
  }

  // ignore schema when comparing objects
  override fun equals(other: Any?): Boolean {
    return (other is SchemaMemberFunction && other.name == this.name && other.receiver == this.receiver && other.parameters == this.parameters && other.semantic == this.semantic)
  }

  override fun getNextLevel(schema: BuildDeclarativeSchema, predicate: (name: String) -> Boolean): List<Entry> = when (semantic) {
    is BlockFunction -> schema.resolveRef(semantic.accessor.fqName)?.let {
      getEntries(schema, it, predicate)
    }
                        ?: listOf() //TODO need to make it universal (b/355179149)
    is PlainFunction -> semantic.getNextLevelPlainFunction(schema, predicate)
  }
}

open class SchemaFunction(open val name: String,
                          open val parameters: List<IdeDataParameter>,
                          open val semantic: FunctionSemantic) : Entry(name) {
  override fun hashCode(): Int {
    return Objects.hashCode(name, parameters, semantic)
  }

  override fun equals(other: Any?): Boolean {
    return (other is SchemaFunction && other.name == this.name && other.parameters == this.parameters && other.semantic == this.semantic)
  }

  override fun getNextLevel(schema: BuildDeclarativeSchema, predicate: (name: String) -> Boolean): List<Entry> = when (semantic) {
    is PlainFunction -> (semantic as PlainFunction).getNextLevelPlainFunction(schema, predicate)
    else -> listOf()
  }
}

private fun PlainFunction.getNextLevelPlainFunction(schema: BuildDeclarativeSchema, predicate: (name: String) -> Boolean): List<Entry> =
  (returnValue as? DataClassRef)?.let { classType ->
    schema.resolveRef(classType.fqName)?.let { getEntries(it, predicate) }
  } ?: listOf()

data class DataProperty(val name: String, val valueType: DataTypeReference) : Entry(name) {
  override fun hashCode(): Int {
    return Objects.hashCode(name, valueType)
  }

  // ignore schema on comparison
  override fun equals(other: Any?): Boolean {
    return (other is DataProperty && other.name == this.name && other.valueType == this.valueType)
  }

  override fun getNextLevel(schema: BuildDeclarativeSchema, predicate: (name: String) -> Boolean): List<Entry> = when (valueType) {
    is DataClassRef -> schema.resolveRef(valueType.fqName)?.let {
      getEntries(schema, it, predicate)
    } ?: listOf()
    is SimpleTypeRef -> listOf() // no next for simple types
    is DataClassRefWithTypes -> schema.resolveRef(valueType.fqName)?.let {
      getEntries(schema, it, predicate)
    } ?: listOf()
    else -> listOf()
  }
}

data class IdeDataParameter(val name: String?, val type: DataTypeReference) : Serializable {}

sealed interface PrimitiveType: Serializable
enum class SimpleDataType : PrimitiveType {
  INT, LONG, STRING, BOOLEAN, UNIT, NULL;
}
data object GenericType : PrimitiveType

sealed class DataTypeReference : Serializable
data class DataClassRef(val fqName: FullName) : DataTypeReference()
data class SimpleTypeRef(val dataType: SimpleDataType) : DataTypeReference()
data object GenericTypeRef : DataTypeReference()
data class DataClassRefWithTypes(val fqName: FullName, val typeArgument: List<GenericTypeArgument>) : DataTypeReference()

interface GenericTypeArgument : Serializable
data class ConcreteGeneric(val reference: DataTypeReference) : GenericTypeArgument
data object StarGeneric : GenericTypeArgument

class BuildDeclarativeSchema(
  var topLevelReceiver: ClassModel?,
  val dataClassesByFqName: Map<FullName, ClassType>,
  val topLevelFunctions: Map<String, SchemaFunction>
) : Serializable {
  fun resolveRef(fqName: FullName): ClassType? = dataClassesByFqName[fqName]

  fun getRootReceiver(): ClassModel {
    return topLevelReceiver ?: throw RuntimeException("topLevelReceiver is null")
  }

  fun getRootEntries(predicate: (String) -> Boolean): List<Entry> =
    getEntries(getRootReceiver(), predicate) + topLevelFunctions.values.filter { predicate(it.name) }

  fun getRootEntries(): List<Entry> =
    getEntries(getRootReceiver()) { true } + topLevelFunctions.values
}

// Idea cannot serialize lazy attributes so we pass schemas in simple wrappers
data class ProjectSchemas(val projects: Set<BuildDeclarativeSchema>) : Serializable
data class SettingsSchemas(val settings: Set<BuildDeclarativeSchema>) : Serializable

// iterates over tree structure
internal fun getEntries(schema: BuildDeclarativeSchema,
                        dataClass: ClassType,
                        predicate: (String) -> Boolean,
                        saw: MutableSet<FullName> = mutableSetOf()): List<Entry> {
  if(saw.contains(dataClass.name)) return listOf() else saw.add(dataClass.name)
  val result = mutableListOf<Entry>()
  if (dataClass is ClassModel) {
    for (superType in dataClass.supertypes) {
      schema.resolveRef(superType)?.let {
        result += getEntries(schema, it, predicate, saw)
      }
    }
  }
  result += getEntries(dataClass, predicate)
  return result.distinct()
}

private fun getEntries(dataClass: ClassType, predicate: (String) -> Boolean): List<Entry> {
  if (dataClass is ClassModel) {
    val result = mutableListOf<Entry>()
    result.addAll(dataClass.properties.filter { predicate(it.name) })
    result.addAll(dataClass.memberFunctions.filter { predicate(it.name) })
    return result
  }
  else return listOf()
}