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

import com.google.common.base.Objects
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import java.io.Serializable

private val EP_NAME: ExtensionPointName<DeclarativeSchemaProvider> =
  ExtensionPointName.create("com.android.tools.gradle.dcl.ide.declarativeSchemaProvider")

interface DeclarativeSchemaProvider {
  fun getSchema(project: Project): BuildDeclarativeSchemas?
}

@Service(Service.Level.PROJECT)
class DeclarativeService(val project: Project) {

  companion object {
    fun getInstance(project: Project) = project.service<DeclarativeService>()
  }

  fun getDeclarativeSchema(): BuildDeclarativeSchemas? {
    val schemas = mutableListOf<BuildDeclarativeSchemas>()
    for (extension in EP_NAME.extensionList) {
      val schema = extension.getSchema(project)
      if (schema != null) schemas.add(schema)
    }
    if (schemas.isNotEmpty()) {
      return schemas.reduce { acc, schema -> acc.merge(schema) }
    }
    return null
  }
}

interface SchemaAwareElement : Serializable {
  val schema: BuildDeclarativeSchema
}

data class FullName(val name: String) : Serializable

// named entry that user can add to file - property, factory, block
sealed class Entry(
  val simpleName: String,
  override val schema: BuildDeclarativeSchema
) : SchemaAwareElement {
  internal fun resolveRef(fqName: FullName): ClassType? = schema.resolveRef(fqName)
  fun getNextLevel(name: String): List<Entry> = getNextLevel { elementName -> elementName == name }
  fun getNextLevel(): List<Entry> = getNextLevel { true }
  internal abstract fun getNextLevel(predicate: (name: String) -> Boolean): List<Entry>
}

sealed interface ClassType{
  val name: FullName
}

data class ClassModel(
  val memberFunctions: List<SchemaFunction>,
  override val name: FullName,
  val properties: List<DataProperty>,
  val supertypes: Set<FullName>,
  override val schema: BuildDeclarativeSchema
) : SchemaAwareElement, ClassType

data class EnumModel(
  override val name: FullName,
  val entryNames: List<String>,
): Serializable, ClassType

sealed class FunctionSemantic : Serializable
data class PlainFunction(val returnValue: DataTypeReference) : FunctionSemantic()
data class BlockFunction(val accessor: DataClassRef) : FunctionSemantic()

data class SchemaFunction(val receiver: DataClassRef,
                          val name: String,
                          val parameters: List<IdeDataParameter>,
                          val semantic: FunctionSemantic,
                          override val schema: BuildDeclarativeSchema) : Entry(name, schema) {
  override fun hashCode(): Int {
    return Objects.hashCode(name, receiver, parameters, semantic)
  }

  // ignore schema when comparing objects
  override fun equals(other: Any?): Boolean {
    return (other is SchemaFunction
            && other.name == this.name
            && other.receiver == this.receiver
            && other.parameters == this.parameters
            && other.semantic == this.semantic)
  }

  override fun getNextLevel(predicate: (name: String) -> Boolean): List<Entry> =
    when (semantic) {
      is BlockFunction -> resolveRef(semantic.accessor.fqName)?.let { getEntries(it, predicate) } ?: listOf()
      //TODO need to make it universal (b/355179149)
      is PlainFunction -> {
        (semantic.returnValue as? DataClassRef)?.let { classType ->
          resolveRef(classType.fqName)?.let { getEntries(it, predicate) }
        } ?: listOf()
      }
    }
}

data class DataProperty(val name: String,
                        val valueType: DataTypeReference,
                        override val schema: BuildDeclarativeSchema) : Entry(name, schema) {

  override fun hashCode(): Int {
    return Objects.hashCode(name, valueType)
  }

  // ignore schema on comparison
  override fun equals(other: Any?): Boolean {
    return (other is DataProperty
            && other.name == this.name
            && other.valueType == this.valueType)
  }

  override fun getNextLevel(predicate: (name: String) -> Boolean): List<Entry> =
    when (valueType) {
      is DataClassRef -> resolveRef(valueType.fqName)?.let { getEntries(it, predicate) } ?: listOf()
      is SimpleTypeRef -> listOf() // no next for simple types
    }
}

data class IdeDataParameter(val name: String?,
                            val type: DataTypeReference) : Serializable

enum class SimpleDataType : Serializable {
  INT, LONG, STRING, BOOLEAN, UNIT, NULL
}

sealed class DataTypeReference : Serializable
data class DataClassRef(val fqName: FullName) : DataTypeReference()
data class SimpleTypeRef(val dataType: SimpleDataType) : DataTypeReference()

class BuildDeclarativeSchema(var topLevelReceiver: ClassModel?, val dataClassesByFqName: Map<FullName, ClassType>) : Serializable {
  internal fun resolveRef(fqName: FullName): ClassType? = dataClassesByFqName[fqName]

  fun getRootReceiver(): ClassModel{
    return topLevelReceiver ?: throw RuntimeException("topLevelReceiver is null")
  }

  fun getRootEntries(predicate: (String) -> Boolean): List<Entry> = getEntries(getRootReceiver(), predicate)

  fun getRootEntries(): List<Entry> = getEntries(getRootReceiver()) { true }
}

// Idea cannot serialize lazy attributes so we pass schemas in simple wrappers
data class ProjectSchemas(val projects: Set<BuildDeclarativeSchema>) : Serializable {
  companion object {
    private const val serialVersionUID = 1L
  }
}
data class SettingsSchemas(val settings: Set<BuildDeclarativeSchema>) : Serializable {
  companion object {
    private const val serialVersionUID = 1L
  }
}
data class BuildDeclarativeSchemas(val settings: Set<BuildDeclarativeSchema>, val projects: Set<BuildDeclarativeSchema>) : Serializable {
  fun merge(schema: BuildDeclarativeSchemas) =
    BuildDeclarativeSchemas(this.settings + schema.settings, this.projects + schema.projects)

  fun getTopLevelEntries(fileName: String): List<Entry> =
    getSchemas(fileName).flatMap { it.getRootEntries() }

  private fun getSchemas(fileName: String) =
    if (isSettings(fileName)) settings else projects

  fun getTopLevelEntriesByName(name: String, fileName: String): List<Entry> =
    getSchemas(fileName).flatMap { it.getRootEntries { existingName: String -> name == existingName } }

  private fun isSettings(name: String) = name == "settings.gradle.dcl"
}

internal fun getEntries(dataClass: ClassType, predicate: (String) -> Boolean): List<Entry> {
  if(dataClass is ClassModel) {
    val result = mutableListOf<Entry>()
    result.addAll(
      dataClass.properties.filter { predicate(it.name) }
    )
    result.addAll(
      dataClass.memberFunctions.filter { predicate(it.name) }
    )
    return result
  } else return listOf()
}
