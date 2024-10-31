/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.execution.common.debug.utils

import com.android.ddmlib.Client
import com.android.tools.idea.execution.common.debug.utils.FacetFinderToken.Companion.findGlobalProcessDefinition
import com.android.tools.idea.execution.common.debug.utils.FacetFinderToken.Companion.hasTestNature
import com.android.tools.idea.execution.common.debug.utils.FacetFinderToken.Companion.isDirectlyDeployable
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.ApplicationProjectContextProvider
import com.android.tools.idea.projectsystem.ApplicationProjectContextProvider.RunningApplicationIdentity.Companion.asRunningApplicationIdentity
import com.android.tools.idea.projectsystem.CommonTestType
import com.android.tools.idea.projectsystem.Token
import com.android.tools.idea.projectsystem.getAndroidFacets
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.getTokenOrNull
import com.android.tools.idea.projectsystem.sourceProviders
import com.android.tools.idea.util.androidFacet
import com.google.common.annotations.VisibleForTesting
import com.intellij.execution.ExecutionException
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.XmlRecursiveElementVisitor
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlFile
import com.intellij.util.graph.GraphAlgorithms
import org.jetbrains.android.facet.AndroidFacet
import java.util.LinkedList
import java.util.Locale

/**
 * Utility class for finding the AndroidFacet that is responsible for the launch of the process with the given name.
 */
object FacetFinder {

  data class Result(val facet: AndroidFacet, val applicationId: String)

  /**
   * Finds a suitable facet by process name to use in debugger attachment configuration.
   *
   * @return The facet to use for attachment configuration. Throws if no suitable facet exists.
   */
  @Throws(ExecutionException::class)
  fun findFacetForProcess(project: Project, client: Client): Result {
    return tryFindFacetForProcess(project, client.asRunningApplicationIdentity())
           ?: throw ExecutionException("Unable to find project context to attach debugger for process ${client.clientData.processName}")
  }
  /**
   * Finds a suitable facet by process name to use in debugger attachment configuration.
   *
   * @return The facet to use for attachment configuration. Null if no suitable facet exists.
   */
  fun tryFindFacetForProcess(project: Project, info: ApplicationProjectContextProvider.RunningApplicationIdentity): Result? {
    return info.heuristicApplicationId?.let { heuristicApplicationId -> findFacetForApplicationId(project, heuristicApplicationId) }
           ?: info.processName?.let { clientDescription -> findFacetForGlobalProcess(project, clientDescription) }
  }

  private fun findFacetForApplicationId(project: Project, applicationId: String): Result? {
    return project.getProjectSystem().findModulesWithApplicationId(applicationId).lastOrNull()?.androidFacet?.let {
      Result(
        facet = it,
        applicationId = applicationId
      )
    }
  }

  /**
   * Finds a suitable facet for a global process.
   *
   * First finds the global process definition, and then if it is in a library, traverses up the graph to find a suitable application.
   */
  private fun findFacetForGlobalProcess(project: Project, processName: String): Result? {
    val definingModule = findGlobalProcessDefinition(project, processName) ?: return null
    val definingModuleSystem = definingModule.getModuleSystem()
    if (isDirectlyDeployable(project, definingModule)) {
      // Global process defined in an application or similar, just return that location.
      val androidFacet = AndroidFacet.getInstance(definingModule) ?: error("AndroidFacet is null")
      return Result(
        facet = androidFacet,
        applicationId = definingModuleSystem.getApplicationIdProvider().let {
          when {
            hasTestNature(project, definingModule) -> it.testPackageName ?: error("testPackageName is null")
            else -> it.packageName
          }
        }
      )
    }
    val moduleManager = ModuleManager.getInstance(project)
    // Global process defined in a library, find modules that depend on that library
    val candidates = GraphAlgorithms.getInstance().findNodeNeighbourhood(moduleManager.moduleGraph(), definingModule, Int.MAX_VALUE)
    // Compare using androidModuleTypeComparator type first to prioritize app-main over app-androidTest.
    val candidate = candidates.maxWithOrNull(
      // Prioritize main over test
      project.getProjectSystem().getProjectSystemModuleTypeComparator().reversed()
        // Then prioritize app and app-like modules over tests and libraries
        .thenComparing(androidModuleTypeComparator)
        // Then prioritize by module dependency order
        .thenComparing(moduleManager.moduleDependencyComparator())
    ) ?: return null
    val androidFacet = AndroidFacet.getInstance(candidate)
      ?: error("${candidate.name} depends on an AndroidModule ${definingModule.name} but AndroidFacet was not found")
    return Result(
      facet = androidFacet,
      applicationId = androidFacet.getModuleSystem().getApplicationIdProvider().packageName
    )
  }

  private val androidModuleTypeComparator: Comparator<Module> = Comparator.comparing {
    // Explicitly prioritize app and similar modules over libraries
    val moduleSystem = it.getModuleSystem()
    when (moduleSystem.type) {
      AndroidModuleSystem.Type.TYPE_NON_ANDROID -> 0
      AndroidModuleSystem.Type.TYPE_LIBRARY -> 1
      AndroidModuleSystem.Type.TYPE_TEST -> 2
      AndroidModuleSystem.Type.TYPE_APP,
      AndroidModuleSystem.Type.TYPE_INSTANTAPP,
      AndroidModuleSystem.Type.TYPE_ATOM,
      AndroidModuleSystem.Type.TYPE_FEATURE,
      AndroidModuleSystem.Type.TYPE_DYNAMIC_FEATURE -> 3
    }
  }
}

/**
 * This [Token] interface should not be considered as an ideal solution to hiding away the project system details from generic IDE
 * code.  The issue is that [FacetFinder] itself operates at the wrong level of abstraction itself, working with [AndroidFacet] or
 * [Module] instances, where those entities themselves are not specific enough: clients, given an [AndroidFacet] result from
 * [FacetFinder], are not actually in a position to proceed in a project-system-independent way, because the relationship between
 * [AndroidFacet]s and other entities (location of code, resources, and so on) is project-system specific.
 *
 * This token does capture what is necessary to separate out support for the Gradle project system from support for the (mostly
 * unused) Default project system, and as such is not useless.  Future maintainers of project systems, or of [FacetFinder] itself,
 * should not consider this interface as the answer to any question beyond "how can I move Gradle-specific implementation details
 * out of the android core module?"
 */
interface FacetFinderToken<P: AndroidProjectSystem> : Token {
  /** Finds the module defining the given global process */
  fun findGlobalProcessDefinition(projectSystem: P, project: Project, processName: String): Module?

  /** Is the [module] directly deployable, or is it more likely a dependency of something else deployable? */
  fun isDirectlyDeployable(projectSystem: P, project: Project, module: Module): Boolean

  /** Does this [module] define only artifacts that use the testPackageId? */
  fun hasTestNature(projectSystem: P, project: Project, module: Module): Boolean

  companion object {
    val EP_NAME = ExtensionPointName<FacetFinderToken<AndroidProjectSystem>>(
      "com.android.tools.idea.execution.common.debug.utils.facetFinderToken")

    fun findGlobalProcessDefinition(project: Project, processName: String): Module? {
      val projectSystem = project.getProjectSystem()
      return when (val token = projectSystem.getTokenOrNull(EP_NAME)) {
        null -> defaultFindGlobalProcessDefinition(project, processName)
        else -> token.findGlobalProcessDefinition(projectSystem, project, processName)
      }
    }

    fun isDirectlyDeployable(project: Project, module: Module): Boolean {
      val projectSystem = project.getProjectSystem()
      return when (val token = projectSystem.getTokenOrNull(EP_NAME)) {
        null -> defaultIsDirectlyDeployable(module)
        else -> token.isDirectlyDeployable(projectSystem, project, module)
      }
    }

    fun hasTestNature(project: Project, module: Module): Boolean {
      val projectSystem = project.getProjectSystem()
      return when (val token = projectSystem.getTokenOrNull(EP_NAME)) {
        null -> defaultHasTestNature(module)
        else -> token.hasTestNature(projectSystem, project, module)
      }
    }

    private fun defaultFindGlobalProcessDefinition(project: Project, processName: String): Module? {
      for (facet in project.getAndroidFacets()) {
        val sourceProviders = facet.sourceProviders.run {
          currentSourceProviders + (currentDeviceTestSourceProviders[CommonTestType.ANDROID_TEST] ?: emptyList())
        }
        for (sourceProvider in sourceProviders) {
          for (manifestFile in sourceProvider.manifestFiles) {
            val globalProcessNames = ProcessNameReader.readGlobalProcessNames(project, manifestFile)
            if (globalProcessNames.contains(processName)) {
              return facet.module
            }
          }
        }
      }
      return null
    }

    private fun defaultIsDirectlyDeployable(module: Module) =
      module.getModuleSystem().type != AndroidModuleSystem.Type.TYPE_LIBRARY

    private fun defaultHasTestNature(module: Module) =
      module.getModuleSystem().type == AndroidModuleSystem.Type.TYPE_TEST
  }
}

/**
 * Utility class for reading the android:process fields of the AndroidManifest.xml files in Android modules.  Should not
 * be used outside FacetFinder-related functionality.
 */
object ProcessNameReader {
  /**
   * Local android processes can be identified (or filtered out) by the existence of
   * this character in their names. For instance, android:process=":localprocessname"
   * in the manifest (which is mapped to com.example.myapplication:localprocessname).
   */
  internal const val LOCAL_PROCESS_NAME_SEPARATOR = ":"

  /**
   * @return the values of the android:process attributes from the manifest file, excluding local processes that start with ":"
   */
  fun readGlobalProcessNames(project: Project, manifestFile: VirtualFile): List<String> {
    val result: MutableList<String> = LinkedList()
    ReadAction.run<RuntimeException> {
      val xmlFile = PsiManager.getInstance(project).findFile(
        manifestFile) as? XmlFile ?: return@run
      xmlFile.accept(object : XmlRecursiveElementVisitor() {
        override fun visitXmlAttribute(attribute: XmlAttribute) {
          if ("process" == attribute.localName) {
            val value: String? = attribute.value

            // Ignore local processes that start with ":" character.
            if (value != null && !value.startsWith(LOCAL_PROCESS_NAME_SEPARATOR)) {
              result.add(value.lowercase(Locale.getDefault()))
            }
          }
        }
      })
    }
    return result
  }
}