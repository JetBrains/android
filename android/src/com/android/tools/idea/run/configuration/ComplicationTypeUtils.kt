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
package com.android.tools.idea.run.configuration

import com.android.SdkConstants
import com.android.annotations.concurrency.WorkerThread
import com.android.tools.deployer.model.component.Complication
import com.android.tools.idea.model.AndroidManifestIndex
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.run.ApkInfo
import com.android.tools.idea.util.androidFacet
import com.android.tools.manifest.parser.BinaryXmlParser
import com.android.tools.manifest.parser.XmlNode
import com.android.zipflinger.ZipRepo
import com.intellij.execution.configurations.RuntimeConfigurationWarning
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import org.jetbrains.android.util.AndroidBundle

/**
 * Return raw complications types from Manifest for given [complicationName] or null if [MergedManifestSnapshot] is not ready.
 *
 * If this method is called while the read lock is being held the method might return null if the Merged Manifest is not ready yet. This is
 * to avoid dead-locks and it's the callers responsibility to retry the call later.
 */
@WorkerThread
fun getComplicationTypesFromManifest(module: Module, complicationName: String): List<String>? {
  val facet = module.androidFacet ?: return emptyList()
  val complicationTypes = runReadAction {
    val contributors = AndroidManifestIndex.getDataForMergedManifestContributors(facet)
    val overrides = facet.getModuleSystem().getManifestOverrides()
    val packageName = facet.getModuleSystem().getPackageName()

    for (manifest in contributors) {
      val service =
        manifest.services.find {
          val name = it.name ?: return@find false
          val resolvedName = overrides.resolvePlaceholders(name)
          val qualifiedName =
            when {
              packageName == null -> resolvedName
              resolvedName.startsWith('.') -> packageName + resolvedName
              resolvedName.contains('.') -> resolvedName
              else -> "$packageName.$resolvedName"
            }
          qualifiedName == complicationName
        }
      if (service != null) {
        val supportedTypes = service.metaData.find { it.name == SdkConstants.VALUE_COMPLICATION_SUPPORTED_TYPES }?.value
        if (supportedTypes != null) {
          return@runReadAction splitTypesString(overrides.resolvePlaceholders(supportedTypes))
        }
      }
    }
    emptyList()
  }

  return complicationTypes
}

internal fun parseRawComplicationTypes(supportedTypesStr: List<String>): List<Complication.ComplicationType> {
  return supportedTypesStr.mapNotNull {
    try {
      Complication.ComplicationType.valueOf(it)
    } catch (e: IllegalArgumentException) {
      null // Ignore unrecognised types, a warning is shows by the [checkRawComplicationTypes] method.
    }
  }
}

internal fun checkRawComplicationTypes(supportedTypesStr: List<String>) {
  for (typeStr in supportedTypesStr) {
    try {
      Complication.ComplicationType.valueOf(typeStr)
    } catch (e: IllegalArgumentException) {
      throw RuntimeConfigurationWarning(AndroidBundle.message("provider.type.invalid.error", typeStr))
    }
  }
}

internal fun getComplicationSourceTypes(apks: Collection<ApkInfo>, componentName: String): List<String> {
  val complicationService = extractServiceXmlNodeFromApks(apks, componentName)
  return extractSupportedComplicationTypes(complicationService)
}

private fun getChildrenWithName(node: XmlNode, name: String) = node.children().filter { it.name() == name }

private fun extractServiceXmlNodeFromApks(apks: Collection<ApkInfo>, componentName: String): XmlNode {
  for (apk in apks) {
    for (apkFileUnit in apk.files) {
      val file = apkFileUnit.apkFile
      val ext = file.name.lowercase()
      if (!ext.endsWith(".apk")) {
        continue
      }
      val parsedXml =
        ZipRepo(file.absolutePath).use { repo ->
          val manifestEntry = repo.getInputStream(SdkConstants.FN_ANDROID_MANIFEST_XML)
          manifestEntry.use { inputStream -> BinaryXmlParser.parse(inputStream) }
        }
      val application = getChildrenWithName(parsedXml, "application").singleOrNull() ?: continue
      val serviceNode = getChildrenWithName(application, "service").find { it.attributes()["name"] == componentName }
      if (serviceNode != null) {
        // Return the first service entry with given [componentName].
        return serviceNode
      }
    }
  }
  throw IllegalStateException("Complication service $componentName is not found in the manifest.")
}

private fun extractSupportedComplicationTypes(service: XmlNode): List<String> {
  val supportedTypesNode = service.children().find { it.attributes()["name"] == SdkConstants.VALUE_COMPLICATION_SUPPORTED_TYPES }
  val rawTypes = supportedTypesNode?.attributes()?.get("value") ?: ""
  return splitTypesString(rawTypes)
}

private fun splitTypesString(types: String): List<String> {
  return types.replace("\\s+".toRegex(), "").split(",")
}
