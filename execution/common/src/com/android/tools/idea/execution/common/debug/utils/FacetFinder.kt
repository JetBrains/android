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
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.ApplicationProjectContextProvider
import com.android.tools.idea.projectsystem.ApplicationProjectContextProvider.RunningApplicationIdentity.Companion.asRunningApplicationIdentity
import com.android.tools.idea.projectsystem.CommonTestType
import com.android.tools.idea.projectsystem.getAndroidFacets
import com.android.tools.idea.projectsystem.getAndroidTestModule
import com.android.tools.idea.projectsystem.getMainModule
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.isAndroidTestModule
import com.android.tools.idea.projectsystem.isMainModule
import com.android.tools.idea.projectsystem.sourceProviders
import com.android.tools.idea.util.androidFacet
import com.google.common.annotations.VisibleForTesting
import com.intellij.execution.ExecutionException
import com.intellij.openapi.application.ReadAction
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
import kotlin.jvm.Throws

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
           ?: throw ExecutionException("Unable to find project context to attach debugger for process ${client.clientData.clientDescription}")
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
   * First finds the global process definition, and then if it is in a library, traverses up the graph to find a suitable applicaiton.
   */
  private fun findFacetForGlobalProcess(project: Project, processName: String): Result? {
    val definingModule = findGlobalProcessDefinition(project, processName) ?: return null
    val definingModuleSystem = definingModule.getModuleSystem()
    if (definingModule.isAndroidTestModule() || definingModuleSystem.type != AndroidModuleSystem.Type.TYPE_LIBRARY) {
      // Global process defined in an application or similar, just return that location.
      val androidFacet = AndroidFacet.getInstance(definingModule) ?: error("AndroidFacet is null")
      return Result(
        facet = androidFacet,
        applicationId = definingModuleSystem.getApplicationIdProvider().let {
          when {
            definingModule.isAndroidTestModule() -> it.testPackageName ?: error("testPackageName is null")
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
      Comparator.comparingInt<Module> { if(it.isMainModule()) 1 else 0 }
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

  /** Finds the module defining the given global process */
  private fun findGlobalProcessDefinition(project: Project, processName: String): Module? {
    for (facet in project.getAndroidFacets()) {
      // Only look at holder modules
      for (sourceProvider in facet.sourceProviders.currentSourceProviders) {
        for (manifestFile in sourceProvider.manifestFiles) {
          val globalProcessNames = ProcessNameReader.readGlobalProcessNames(project, manifestFile)
          if (globalProcessNames.contains(processName)) {
            return facet.module.getMainModule()
          }
        }
      }
      for (sourceProvider in facet.sourceProviders.currentDeviceTestSourceProviders[CommonTestType.ANDROID_TEST] ?: emptyList()) {
        for (manifestFile in sourceProvider.manifestFiles) {
          val globalProcessNames = ProcessNameReader.readGlobalProcessNames(project, manifestFile)
          if (globalProcessNames.contains(processName)) {
            return facet.module.getAndroidTestModule()
          }
        }
      }
    }
    return null
  }
}

/**
 * Utility class for reading the android:process fields of the AndroidManifest.xml files in Android modules.
 */
@VisibleForTesting
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
  @VisibleForTesting
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