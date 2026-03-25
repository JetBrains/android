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
package com.android.tools.idea.script

import com.android.tools.idea.flags.StudioFlags.ANALYSIS_SCRIPTS
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.openapi.project.Project
import java.io.File
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ScriptAcceptedLocation
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.acceptedLocations
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.intellij.ScriptDefinitionsProvider
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath
import kotlin.script.experimental.jvm.util.classpathFromClassloader

const val IDE_SCRIPT_EXTENSION = "ide.kts"

@KotlinScript(
  displayName = "IDE Script",
  fileExtension = IDE_SCRIPT_EXTENSION,
  compilationConfiguration = IdeScriptCompilationConfiguration::class,
)
abstract class IdeScript(val project: Project) {
  var result: String? = null
}

class IdeScriptCompilationConfiguration :
  ScriptCompilationConfiguration({
    defaultImports(Project::class)
    jvm { updateClasspath(ideScriptClasspath) }
    ide { acceptedLocations(ScriptAcceptedLocation.Everywhere) }
  })

class IdeScriptDefinitionProvider : ScriptDefinitionsProvider {
  override val id: String
    get() = "IdeScriptDefinitionProvider"

  override fun getDefinitionClasses() =
    if (ANALYSIS_SCRIPTS.get()) {
      listOf(IdeScript::class.qualifiedName!!)
    } else {
      emptyList()
    }

  override fun getDefinitionsClassPath() = ideScriptClasspath ?: emptyList()

  override fun useDiscovery() = false
}

// Note that when using the "Run Android Studio" configuration from IntelliJ, the class loader setup is quite different, and is essentially
// "more forgiving" (most class loaders will have access to all classes). The code below, and in particular, the overriding of
// getDefinitionsClassPath() above, is necessary when actually running a proper Android Studio release (or when running via Bazel).

val ideScriptClasspath by lazy { getClasspathFromClassLoader(IdeScript::class.java.classLoader) }

@Suppress("UnstableApiUsage")
private fun getClasspathFromClassLoader(classLoader: ClassLoader): List<File>? {
  if (classLoader !is PluginClassLoader) return classpathFromClassloader(classLoader)

  // Workaround necessary for out-of-date code in classpathFromClassloader that cannot find
  // parents of PluginClassLoader.

  val result = LinkedHashSet<File>()
  classpathFromClassloader(classLoader)?.let { result.addAll(it) }

  // Includes transitive parents (so really, ancestors).
  for (parent in classLoader.getAllParentsClassLoaders()) {
    classpathFromClassloader(parent)?.let { result.addAll(it) }
  }

  return result.toList()
}
