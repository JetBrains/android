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
package com.android.tools.idea.gradle.declarative

import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessModuleDir
import org.gradle.internal.declarativedsl.analysis.AnalysisSchema
import org.gradle.internal.declarativedsl.analysis.DataClass
import org.gradle.internal.declarativedsl.analysis.DataTypeRef
import org.gradle.internal.declarativedsl.analysis.FqName
import org.gradle.internal.declarativedsl.analysis.FunctionSemantics
import org.gradle.internal.declarativedsl.analysis.SchemaMemberFunction
import org.gradle.internal.declarativedsl.serialization.SchemaSerialization
import java.io.File

/**
 * Gets and caches declarative schema.
 */
@Service(Service.Level.PROJECT)
class DeclarativeService {
  val map = HashMap<Module, DeclarativeSchema>()

  companion object {
    fun getInstance(project: Project) = project.service<DeclarativeService>()
    val log = Logger.getInstance(DeclarativeService::class.java)
  }

  fun getSchema(module: Module): DeclarativeSchema? {
    if (!StudioFlags.GRADLE_DECLARATIVE_IDE_SUPPORT.get()) return null
    return map.getOrPut(module) {
      val parentPath = module.guessModuleDir()?.path
      val schemaFolder = File(parentPath, ".gradle/declarative-schema")
      schemaFolder.lastModified()
      val paths = schemaFolder.list { _: File?, name: String -> name.endsWith(".dcl.schema") } ?: return null
      val schemas = mutableListOf<AnalysisSchema>()
      var failure = false
      for (path in paths) {
        try {
          val schema = File(schemaFolder, path)
          val analysisSchema = SchemaSerialization.schemaFromJsonString(schema.readText())
          schemas.add(analysisSchema)
        }
        catch (e: Exception) {
          failure = true
          log.warn("Declarative schema parsing error: $e")
        }
      }
      return if (schemas.isNotEmpty())
        DeclarativeSchema(schemas, failure)
      else null
    }
  }
}

class DeclarativeSchema(private val schemas: List<AnalysisSchema>, val failureHappened: Boolean) {
  private val _dataClassesByFqName: Map<FqName, DataClass> by lazy {
    schemas.fold(mapOf()) { acc, e -> acc + e.dataClassesByFqName }
  }
  private val _rootMemberFunctions: List<SchemaMemberFunction> by lazy {
    schemas.fold(listOf()) { acc, e -> acc + e.topLevelReceiverType.memberFunctions }
  }

  fun getDataClassesByFqName(): Map<FqName, DataClass> = _dataClassesByFqName

  fun getRootMemberFunctions(): List<SchemaMemberFunction> = _rootMemberFunctions
}

fun getTopLevelReceiverByName(name: String, schema: DeclarativeSchema): FqName? {
  getReceiverByName(name, schema.getRootMemberFunctions())?.let {
    return it
  }
  // this is specific case for settings.gradle.dcl - hopefully, eventually schema file will be fixed
  // to have all settingsInternal attributes in rootMembers
  schema.getDataClassesByFqName()[FqName("org.gradle.api.internal", "SettingsInternal")]?.let {
    return it.properties.find { it.name == name }?.type?.fqName()
  }
  return null

}

private fun DataTypeRef.fqName() = (this as? DataTypeRef.Name)?.fqName

fun getReceiverByName(name: String, memberFunctions: List<SchemaMemberFunction>): FqName? {
  val dataMemberFunction = memberFunctions.find { it.simpleName == name } ?: return null
  (dataMemberFunction.semantics as? FunctionSemantics.AccessAndConfigure)?.accessor?.let {
    return it.objectType.fqName()
  }
  dataMemberFunction.receiver.fqName()?.let { return it }
  return null
}