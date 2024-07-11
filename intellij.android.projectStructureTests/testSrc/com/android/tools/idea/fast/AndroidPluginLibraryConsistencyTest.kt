// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.fast

import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.buildNsUnawareJdom
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import org.jdom.Attribute
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.serialization.JDomSerializationUtil
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.readText

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
  fun `'studio-platform' and 'studio-test-platform' libraries are defined with a proper scope`() {
    androidModuleLibrariesCatalog.forEach { (moduleName, moduleLibraries) ->
      val studioPlatformLibraries = moduleLibraries
        .filter { it.libraryName == "studio-platform" || it.libraryName == "studio-test-platform" }
        .distinctBy { it.libraryName }

      val moduleLibraryDiffReport = ModuleLibraryDiffReport.NO_DIFF

      studioPlatformLibraries.forEach { library ->
        val expectedLibraryScope = when (library.libraryName) {
          "studio-platform" -> JpsJavaDependencyScope.PROVIDED
          "studio-test-platform" -> JpsJavaDependencyScope.TEST
          else -> library.scope
        }

        if (library.scope != expectedLibraryScope) {
          moduleLibraryDiffReport.addReport(
            library,
            expectedLibraryScope,
            "Module library '${library.libraryName}' in module '${moduleName}' should be defined with a '$expectedLibraryScope' scope."
          )
        }
      }

      if (moduleLibraryDiffReport.hasReports()) {
        moduleLibraryDiffReport.reportToErrorCollector(errorCollector)
      }
    }
  }

  private data class ModuleLibrary(
    val libraryName: String,
    val scope: JpsJavaDependencyScope,
    val moduleName: String,
    val moduleImlFilePath: Path,
  )

  private class ModuleLibraryDiffReport private constructor(
    private val libraryToExpectedScope: HashMap<ModuleLibrary, JpsJavaDependencyScope> = HashMap(),
    private val messages: MutableList<String> = ArrayList(),
  ) {

    fun addReport(
      library: ModuleLibrary,
      expectedLibraryScope: JpsJavaDependencyScope,
      message: String,
    ) {
      assertLibrariesAreDefinedInTheSameModule(libraryToExpectedScope.keys + library)
      require(message.isNotEmpty()) { "Message should not be blank." }

      libraryToExpectedScope[library] = expectedLibraryScope
      messages += message
    }

    fun hasReports(): Boolean {
      return libraryToExpectedScope.isNotEmpty() && messages.isNotEmpty()
    }

    fun reportToErrorCollector(errorCollector: ErrorCollector) {
      val library = libraryToExpectedScope.keys.firstOrNull() ?: error("No reports made.")
      val moduleImlFilePath = library.moduleImlFilePath

      val message = messages.joinToString("\n")

      val actualModulesImlContent = moduleImlFilePath.readText()
      val expectedModuleImlContent = generateExpectedModuleImlContent(
        moduleImlFilePath,
        libraryToExpectedScope.mapKeys { it.key.libraryName }
      )

      errorCollector.addError(FileComparisonFailedError(
        message,
        expectedModuleImlContent,
        actualModulesImlContent,
        null,
        moduleImlFilePath.absolutePathString()
      ))
    }

    private fun assertLibrariesAreDefinedInTheSameModule(libraries: Set<ModuleLibrary>) {
      val distinctModules = libraries.map { it.moduleName }.distinct()
      require(distinctModules.count() == 1) {
        "Libraries should be defined in a single module. More than one module exists in reports: $distinctModules"
      }
    }


    private fun generateExpectedModuleImlContent(
      moduleImlFilePath: Path,
      missConfiguredLibrariesToLibraryScope: Map<String, JpsJavaDependencyScope>,
    ): String {
      val element = buildNsUnawareJdom(moduleImlFilePath)
      val modulesRootManagerElement = JDomSerializationUtil.findComponent(element, "NewModuleRootManager")!!
      val libraryElements = modulesRootManagerElement
        .children
        .filter { child ->
          val isLibraryType = child.getAttributeValue("type") == "library"
          val isKnownMissConfiguredLibrary = missConfiguredLibrariesToLibraryScope.keys.contains(child.getAttributeValue("name"))

          isLibraryType && isKnownMissConfiguredLibrary
        }

      libraryElements.forEach { libraryElement ->
        val expectedScope = missConfiguredLibrariesToLibraryScope[libraryElement.getAttributeValue("name")]!!.name
        libraryElement.attributes.add(1, Attribute("scope", expectedScope))
      }

      return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n${JDOMUtil.write(element).trimEnd('\n')}"
    }

    companion object {
      val NO_DIFF: ModuleLibraryDiffReport
        get() = ModuleLibraryDiffReport()
    }
  }
}