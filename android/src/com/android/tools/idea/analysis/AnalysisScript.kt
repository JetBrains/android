/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.analysis

import com.android.tools.idea.flags.StudioFlags.ANALYSIS_SCRIPTS
import com.intellij.openapi.project.Project
import java.io.File
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ScriptAcceptedLocation
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.acceptedLocations
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.intellij.ScriptDefinitionsProvider
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm

const val ANALYSIS_SCRIPT_EXTENSION = "analysis.kts"

@KotlinScript(
  displayName = "Code Analysis Script",
  fileExtension = ANALYSIS_SCRIPT_EXTENSION,
  compilationConfiguration = AnalysisScriptCompilationConfiguration::class,
)
abstract class AnalysisScript(val project: Project) {
  var result: String? = null
}

class AnalysisScriptCompilationConfiguration :
  ScriptCompilationConfiguration({
    defaultImports(Project::class)
    jvm { dependenciesFromCurrentContext(wholeClasspath = true) }
    ide { acceptedLocations(ScriptAcceptedLocation.Everywhere) }
  })

class AnalysisScriptDefinitionProvider : ScriptDefinitionsProvider {
  override val id: String
    get() = "AnalysisScriptDefinitionProvider"

  override fun getDefinitionClasses() =
    if (ANALYSIS_SCRIPTS.get()) {
      listOf(AnalysisScript::class.qualifiedName!!)
    } else {
      emptyList()
    }

  override fun getDefinitionsClassPath() = emptyList<File>()

  override fun useDiscovery() = false
}
