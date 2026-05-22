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
package com.android.tools.idea.publishing.play

import com.android.aapt.ConfigurationOuterClass
import com.android.aapt.Resources
import com.android.ide.common.xml.AndroidManifestParser
import com.android.tools.apk.analyzer.Archive
import com.android.tools.apk.analyzer.Archives
import com.android.tools.idea.apk.viewer.ProtoXmlPrettyPrinterImpl
import com.android.utils.XmlUtils
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element

data class AppMetadata(val appName: String?, val packageName: String?, val versionCode: String?, val versionName: String?)

suspend fun extractAppMetadata(path: Path): AppMetadata =
  withContext(Dispatchers.IO) {
    Archives.open(path).use { archiveContext ->
      val archive = archiveContext.archive

      // 1. Extract and Decode Manifest
      val manifestXml = extractAndDecodeManifest(archive)

      // 2. Parse Manifest Data (Package, Version Code, Version Name)
      val manifestData =
        ByteArrayInputStream(manifestXml.toByteArray(StandardCharsets.UTF_8)).use { stream -> AndroidManifestParser.parse(stream) }

      // 3. Get App Name Reference
      val appName = extractAppName(manifestXml, archive)

      AppMetadata(
        appName = appName,
        packageName = manifestData.getPackage(),
        versionCode = manifestData.versionCode?.toString(),
        versionName = manifestData.versionName,
      )
    }
  }

private fun extractAndDecodeManifest(archive: Archive): String {
  val manifestPath = archive.contentRoot.resolve("base/manifest/AndroidManifest.xml")
  require(Files.exists(manifestPath)) { "Manifest not found in AAB" }

  val bytes = manifestPath.readBytes()

  return ProtoXmlPrettyPrinterImpl().prettyPrint(bytes)
}

private fun extractAppName(manifestXml: String, archive: Archive): String? {
  val doc = XmlUtils.parseDocumentSilently(manifestXml, true) ?: return null
  val appElement = doc.getElementsByTagName("application").item(0) as? Element ?: return null
  val labelRef = appElement.getAttribute("android:label").takeIf { it.isNotEmpty() } ?: return null

  // If it's a literal string, return it
  if (!labelRef.startsWith("@")) {
    return labelRef
  }

  // It's a resource reference (e.g. @string/app_name)
  val resName = labelRef.substringAfter('/')

  return resolveAabResource(archive, resName)
}

// Resolves AAB resource from base/resources.pb (Proto format)
private fun resolveAabResource(archive: Archive, resourceName: String): String {
  val pbPath = archive.contentRoot.resolve("base/resources.pb")
  if (!Files.exists(pbPath)) return resourceName

  Files.newInputStream(pbPath).use { stream ->
    val resourceTable = Resources.ResourceTable.parseFrom(stream)
    // Explicitly cast lists to standard Kotlin List to avoid ambiguous iterator issues
    val packages = resourceTable.packageList as List<Resources.Package>
    packages.forEach { pkg ->
      val types = pkg.typeList as List<Resources.Type>
      val stringType = types.firstOrNull { it.name == "string" } ?: return@forEach

      val entries = stringType.entryList as List<Resources.Entry>
      val entry = entries.firstOrNull { it.name == resourceName } ?: return@forEach

      val configValues = entry.configValueList as List<Resources.ConfigValue>
      val resolvedValue =
        configValues
          .sortedBy { it.config != ConfigurationOuterClass.Configuration.getDefaultInstance() }
          .map { it.value }
          .firstNotNullOfOrNull { value -> value.item.str.value.takeIf { value.item.hasStr() } }
      if (resolvedValue != null) return resolvedValue
    }
  }
  return resourceName
}
