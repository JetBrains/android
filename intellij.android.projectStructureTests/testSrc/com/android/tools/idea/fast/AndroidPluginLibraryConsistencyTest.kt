// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.fast

import org.hamcrest.CoreMatchers
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector
import java.nio.file.Path

class AndroidPluginLibraryConsistencyTest : AndroidPluginProjectConsistencyTestCase() {
  @Rule
  @JvmField
  val errorCollector: ErrorCollector = ErrorCollector()

  private val androidModuleLibrariesCatalog: Map<String, List<ModuleLibrary>> = androidProject
    .modules
    .associateBy(keySelector = { it.name }) { module ->
      module.libraryDependencies.map { library ->
        val ref = library.libraryReference
        val moduleName = module.name
        val moduleImlFilePath = module.imlFilePath
        val libraryName = ref.libraryName
        val libraryScope = JpsJavaExtensionService.getInstance().getDependencyExtension(library)!!.scope

        ModuleLibrary(libraryName, libraryScope, moduleName, moduleImlFilePath)
      }
    }

  @Test
  fun `'studio-platform' and 'studio-platform-test' libraries are defined with a proper scope`() {
    androidModuleLibrariesCatalog.forEach { (moduleName, moduleLibraries) ->
      moduleLibraries
        .forEach { moduleLibrary ->
          val libraryName = moduleLibrary.libraryName
          val expectedLibraryScope = moduleLibrary.expectedLibraryScope()

          errorCollector.checkThat(
            "Module library '$libraryName' in module '${moduleName}' should be defined with a '$expectedLibraryScope' scope." +
            "\nModule file location: ${moduleLibrary.moduleImlFilePath}",
            moduleLibrary.scope.name,
            CoreMatchers.`is`(expectedLibraryScope.name)
          )
        }
    }
  }

  private data class ModuleLibrary(
    val libraryName: String,
    val scope: JpsJavaDependencyScope,
    val moduleName: String,
    val moduleImlFilePath: Path,
  ) {
    fun expectedLibraryScope(): JpsJavaDependencyScope {
      return when (libraryName) {
        "studio-platform" -> JpsJavaDependencyScope.PROVIDED
        "studio-platform-test" -> JpsJavaDependencyScope.TEST
        else -> scope
      }
    }
  }
}