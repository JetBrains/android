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
import com.android.tools.idea.model.MergedManifestManager
import com.android.tools.idea.model.MergedManifestSnapshot
import com.android.tools.idea.run.ApkInfo
import com.android.tools.manifest.parser.BinaryXmlParser
import com.android.tools.manifest.parser.XmlNode
import com.android.utils.forEach
import com.android.zipflinger.ZipRepo
import com.intellij.execution.configurations.RuntimeConfigurationWarning
import org.jetbrains.android.util.AndroidBundle

/**
 * Return raw complications types from Manifest for given [complicationName] or null if [MergedManifestSnapshot] is not ready.
 */
@WorkerThread
internal fun getComplicationTypesFromManifest(module: com.intellij.openapi.module.Module, complicationName: String): List<String> =
  extractSupportedComplicationTypes(
    MergedManifestManager.getMergedManifestSupplier(module).get().get(),
    complicationName)

internal fun parseRawComplicationTypes(supportedTypesStr: List<String>): List<Complication.ComplicationType> {
  return supportedTypesStr.mapNotNull {
    try {
      Complication.ComplicationType.valueOf(it)
    }
    catch (e: IllegalArgumentException) {
      null // Ignore unrecognised types, a warning is shows by the [checkRawComplicationTypes] method.
    }
  }
}

internal fun checkRawComplicationTypes(supportedTypesStr: List<String>) {
  for (typeStr in supportedTypesStr) {
    try {
      Complication.ComplicationType.valueOf(typeStr)
    }
    catch (e: IllegalArgumentException) {
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
      val parsedXml = ZipRepo(file.absolutePath).use { repo ->
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

internal fun extractSupportedComplicationTypes(snapshot: MergedManifestSnapshot, complicationServiceName: String): List<String> {
  val complicationTag = snapshot.services.find {
    complicationServiceName == it.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
  } ?: return emptyList()
  val metaDataTags = complicationTag.getElementsByTagName(SdkConstants.TAG_META_DATA)
  metaDataTags.forEach {
    val metaDataType = it.attributes.getNamedItemNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)?.nodeValue
    if (metaDataType == SdkConstants.VALUE_COMPLICATION_SUPPORTED_TYPES) {
      val rawTypes = it.attributes.getNamedItemNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_VALUE)?.nodeValue ?: ""
      return splitTypesString(rawTypes)
    }
  }
  return emptyList()
}

private fun splitTypesString(types: String): List<String> {
  return types.replace("\\s+".toRegex(), "").split(",")
}