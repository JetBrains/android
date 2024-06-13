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

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
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
  }

  fun getSchema(module: Module): DeclarativeSchema? {
    return map.getOrPut(module) {
      val parentPath = module.guessModuleDir()?.path
      val project = File(parentPath, ".gradle/declarative-schema/project.dcl.schema")
      val plugins = File(parentPath, ".gradle/declarative-schema/plugins.dcl.schema")
      try {
        val projectSchema = SchemaSerialization.schemaFromJsonString(project.readText())
        val pluginSchema = SchemaSerialization.schemaFromJsonString(plugins.readText())
        DeclarativeSchema(projectSchema, pluginSchema)
      }
      catch (e: Exception) {
        return null
      }
    }
  }
}

class DeclarativeSchema(private val project: AnalysisSchema, private val plugin: AnalysisSchema) {
  fun getDataClassesByFqName(): Map<FqName, DataClass> = project.dataClassesByFqName + plugin.dataClassesByFqName
  fun getRootMemberFunctions(): List<SchemaMemberFunction> =
    project.topLevelReceiverType.memberFunctions + plugin.topLevelReceiverType.memberFunctions
}
fun getTopLevelReceiverByName(name: String, schema: DeclarativeSchema): FqName? =
  getReceiverByName(name, schema.getRootMemberFunctions())

fun getReceiverByName(name: String, memberFunctions: List<SchemaMemberFunction>): FqName? {
  val dataMemberFunction = memberFunctions.find { it.simpleName == name } ?: return null
  val accessor = (dataMemberFunction.semantics as? FunctionSemantics.AccessAndConfigure)?.accessor
  return (accessor?.objectType as? DataTypeRef.Name)?.fqName
}