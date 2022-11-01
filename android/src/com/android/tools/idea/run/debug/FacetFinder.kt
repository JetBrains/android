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
package com.android.tools.idea.run.debug

import com.android.tools.idea.projectsystem.getAndroidFacets
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.run.ApkProvisionException
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.XmlRecursiveElementVisitor
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.SourceProviderManager.Companion.getInstance
import org.jetbrains.annotations.NonNls
import java.util.LinkedList
import java.util.Locale

/**
 * Utility class for finding the AndroidFacet that is responsible for the launch of the process with the given name.
 */
object FacetFinder {

  /**
   * Finds a facet by package name to use in debugger attachment configuration.
   *
   * @return The facet to use for attachment configuration. Null if no suitable facet exists.
   */
  fun findFacetForProcess(project: Project, processName: String): AndroidFacet? {
    val facets = project.getAndroidFacets()
    for (facet in facets) {
      try {
        val facetPackageName = facet.getModuleSystem().getApplicationIdProvider().packageName
        if (processName == facetPackageName) {
          return facet
        }

        // The facet's package name does not match the given package name. Also check for local/global
        // processes.

        // Handle local processes under the package. E.g., android:processname=":"foo" causes the process
        // name to be "com.example.myapplication:foo".
        if (processName.startsWith(facetPackageName + ProcessNameReader.LOCAL_PROCESS_NAME_SEPARATOR)) {
          return facet
        }

        // Search the facet's  AndroidManifest.xml to see if it contains any android:process="..."
        // that create global processes where the package name will not match the process name.
        if (ProcessNameReader.hasGlobalProcess(facet, processName)) {
          // This module has an android:process override that matches the name of the process being attached to.
          return facet
        }
      }
      catch (e: ApkProvisionException) {
        Logger.getInstance(FacetFinder::class.java).warn(e)
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
   * @param facet The facet whose AndroidManifest.xml file will be searched
   * @param processName  The process name being searched
   * @return True if the provided facet contains a global process with the provided name
   */
  fun hasGlobalProcess(facet: AndroidFacet, processName: String): Boolean {
    return readGlobalProcessNames(facet)
      .stream()
      .anyMatch { globalProcessName: String -> processName == globalProcessName }
  }

  /**
   * @param facet The facet whose AndroidManifest.xml will be searched
   * @return the values of the android:process attributes from the manifest file, excluding local processes that start with ":"
   */
  private fun readGlobalProcessNames(facet: AndroidFacet): List<String> {
    val manifestFile = getInstance(facet).mainManifestFile ?: return emptyList()
    val result: MutableList<String> = LinkedList()
    ReadAction.run<RuntimeException> {
      val xmlFile = PsiManager.getInstance(facet.module.project).findFile(
        manifestFile) as? XmlFile ?: return@run
      xmlFile.accept(object : XmlRecursiveElementVisitor() {
        override fun visitXmlAttribute(attribute: XmlAttribute) {
          if ("process" == attribute.localName) {
            val value: @NonNls String? = attribute.value

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