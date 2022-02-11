/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.android.SdkConstants.VALUE_COMPLICATION_SUPPORTED_TYPES
import com.android.tools.deployer.model.component.Complication
import com.android.tools.idea.model.MergedManifestSnapshot
import com.android.tools.idea.run.ApkInfo
import com.android.tools.manifest.parser.BinaryXmlParser
import com.android.tools.manifest.parser.XmlNode
import com.android.utils.forEach
import com.android.zipflinger.ZipRepo
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.psi.KtClass
import kotlin.IllegalStateException

object WearBaseClasses {
  val WATCH_FACES = arrayOf(SdkConstants.CLASS_WATCHFACE_WSL, SdkConstants.CLASS_WATCHFACE_ANDROIDX)
  val COMPLICATIONS = arrayOf(SdkConstants.CLASS_COMPLICATION_SERVICE_ANDROIDX, SdkConstants.CLASS_COMPLICATION_SERVICE_WSL)
  val TILES = arrayOf(SdkConstants.CLASS_TILE_SERVICE)
}

internal val Executor.isDebug: Boolean
  get() = DefaultDebugExecutor.EXECUTOR_ID == this.id

internal fun PsiElement?.getPsiClass(): PsiClass? {
  return when (val parent = this?.parent) {
    is KtClass -> parent.toLightClass()
    is PsiClass -> parent
    else -> null
  }
}

internal fun getChildrenWithName(node: XmlNode, name: String): List<XmlNode> {
  return node.children().filter {
    it.name() == name
  }
}

internal fun extractXmlNodeFromApk(apks : Collection<ApkInfo>) : XmlNode  {
  for (apk in apks) {
    for (apkFileUnit in apk.files) {
      val file = apkFileUnit.apkFile
      val ext = file.name.lowercase()
      if (!ext.endsWith(".apk")) {
        continue
      }
      ZipRepo(file.absolutePath).use { repo ->
        val manifestEntry = repo.getInputStream(SdkConstants.FN_ANDROID_MANIFEST_XML)
        manifestEntry.use { inputStream -> return BinaryXmlParser.parse(inputStream) }
      }
    }
  }
  throw IllegalStateException("Manifest file not found.")
}

internal fun extractServiceFromXmlFileNode(parsedXml: XmlNode, componentName: String) : XmlNode {
  for (application in getChildrenWithName(parsedXml, "application")) {
    return getChildrenWithName(application, "service").find { it.attributes()["name"] == componentName } ?: continue
  }
  throw IllegalStateException("Complication service not found in the manifest.")
}

internal fun extractComplicationSupportedTypes(service: XmlNode): List<Complication.ComplicationType> {
  val supportedTypesNode = service.children().find { it.attributes()["name"] == VALUE_COMPLICATION_SUPPORTED_TYPES }
  val rawTypes =  supportedTypesNode?.attributes()?.get("value") ?: ""
  return parseRawTypes(rawTypes)
}

internal fun extractComplicationSupportedTypes(snapshot: MergedManifestSnapshot, complicationServiceName: String):
  List<Complication.ComplicationType> {
  val complicationTag = snapshot.services.find {
    complicationServiceName == it.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
  } ?: return emptyList()
  val metaDataTags = complicationTag.getElementsByTagName(SdkConstants.TAG_META_DATA)
  metaDataTags.forEach {
    val metaDataType = it.attributes.getNamedItemNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)?.nodeValue
    if (metaDataType == VALUE_COMPLICATION_SUPPORTED_TYPES) {
      val rawTypes = it.attributes.getNamedItemNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_VALUE)?.nodeValue ?: ""
      return parseRawTypes(rawTypes)
    }
  }
  return emptyList()
}

internal fun parseRawTypes(rawTypes: String): List<Complication.ComplicationType>{
  val supportedTypesStr = mutableListOf<String>()
  supportedTypesStr.addAll(rawTypes.split(","))
  val supportedTypes = mutableListOf<Complication.ComplicationType>()
  for (typeStr in supportedTypesStr) {
    try {
      supportedTypes.add(Complication.ComplicationType.valueOf(typeStr))
    } catch (e: IllegalArgumentException) {
      throw RuntimeConfigurationException(AndroidBundle.message("provider.type.invalid.error", typeStr));
    }
  }
  return supportedTypes
}