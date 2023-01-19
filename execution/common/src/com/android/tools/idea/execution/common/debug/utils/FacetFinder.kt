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

import com.android.tools.idea.projectsystem.SourceProviderManager.Companion.getInstance
import com.android.tools.idea.projectsystem.getAndroidFacets
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.run.ApkProvisionException
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.XmlRecursiveElementVisitor
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.facet.AndroidFacet
import java.util.LinkedList
import java.util.Locale

/**
 * Utility class for finding the AndroidFacet that is responsible for the launch of the process with the given name.
 */
object FacetFinder {

  /**
   * Finds a facet by process name to use in debugger attachment configuration.
   *
   * @return The facet to use for attachment configuration. Null if no suitable facet exists.
   */
  fun findFacetForProcess(project: Project, processName: String): AndroidFacet? {
    // Local cache so that we don't read the same AndroidManifest.xml file more than once.
    val processNameCache = mutableMapOf<AndroidFacet, List<String>>()

    val facets = project.getAndroidFacets()
    for (facet in facets) {
      val facetPackageName = try {
        facet.getModuleSystem().getApplicationIdProvider().packageName
      }
      catch (e: ApkProvisionException) {
        Logger.getInstance(FacetFinder::class.java).debug(e)
        continue  // Only scan facets that have an applicationId (i.e., app modules)
      }

      if (processName == facetPackageName) {
        return facet
      }

      // The facet's package name does not match the given process name. Also check for local/global
      // processes that correspond to this facet.

      // Handle local processes, e.g., android:process=":foo" causes the process name to be
      // "com.example.myapplication:foo".
      // This can identify any local processes that are defined by library modules that this module
      // depends on, because the process names of all such local processes would start with this module's
      // applicationId. That's why we don't need to separately search library modules for local processes.
      if (processName.startsWith(facetPackageName + ProcessNameReader.LOCAL_PROCESS_NAME_SEPARATOR)) {
        return facet
      }

      // Handle global processes, e.g., android:process="anything" causes the process name to be "anything".
      // Note that we must search not only the current facet, but also the facets for all modules that the
      // current module depends on, as any one of them might contain a global process definition.
      val dependentModules = mutableSetOf<com.intellij.openapi.module.Module>()
      ModuleUtilCore.getDependencies(facet.mainModule, dependentModules)
      for (dependentModule in dependentModules) {
        val dependentAndroidFacet = AndroidFacet.getInstance(dependentModule) ?: continue
        val globalProcessNames = processNameCache.getOrPut(dependentAndroidFacet) {
          ProcessNameReader.readGlobalProcessNames(dependentAndroidFacet)
        }
        if (globalProcessNames.contains(processName)) {
          // This module has an android:process override that matches the name of the process being attached to.
          // Note that we don't return the facet for the dependent module, because it  wouldn't be able to provide
          // applicationId that is needed by attachment configuration. Instead, we return the facet for the original
          // module.
          return facet
        }
      }
    }

    return null
  }
}

/**
 * Utility class for reading the android:process fields of the AndroidManifest.xml files in Android modules.
 */
object ProcessNameReader {
  /**
   * Local android processes can be identified (or filtered out) by the existence of
   * this character in their names. For instance, android:process=":localprocessname"
   * in the manifest (which is mapped to com.example.myapplication:localprocessname).
   */
  const val LOCAL_PROCESS_NAME_SEPARATOR = ":"

  /**
   * @param facet The facet whose AndroidManifest.xml will be searched
   * @return the values of the android:process attributes from the manifest file, excluding local processes that start with ":"
   */
  fun readGlobalProcessNames(facet: AndroidFacet): List<String> {
    val manifestFile = getInstance(facet).mainManifestFile ?: return emptyList()
    val result: MutableList<String> = LinkedList()
    ReadAction.run<RuntimeException> {
      val xmlFile = PsiManager.getInstance(facet.module.project).findFile(
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